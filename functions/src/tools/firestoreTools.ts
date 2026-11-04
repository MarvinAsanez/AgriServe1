import * as admin from 'firebase-admin';

// Initialize Firebase Admin if not already initialized
if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

export interface CheckStockAvailabilityParams {
  itemName?: string;
  category?: string;
}

export interface InventoryItemResult {
  id: string;
  itemName: string;
  category: string;
  quantity: number;
  unit: string;
  status: 'In Stock' | 'Low Stock' | 'Out of Stock';
  updatedAt?: string;
}

export interface GetRequestStatusParams {
  requestId: string;
}

export interface EscalateToStaffParams {
  issueSummary: string;
  category?: string;
}

/**
 * 1. checkStockAvailability
 * Queries the `inventory_items` collection in Firestore matching category or itemName.
 * Categorizes availability status based on current quantity.
 */
export async function checkStockAvailability(
  params: CheckStockAvailabilityParams
): Promise<{ success: boolean; totalFound: number; items: InventoryItemResult[]; message?: string }> {
  try {
    const { itemName, category } = params;
    let query: admin.firestore.Query = db.collection('inventory_items');

    // Normalize category if provided (case-insensitive mapping to standard AgriServe categories)
    let normalizedCategory: string | undefined = undefined;
    if (category) {
      const lowerCat = category.trim().toLowerCase();
      if (lowerCat.includes('seed')) normalizedCategory = 'Seeds';
      else if (lowerCat.includes('fertiliz') || lowerCat.includes('abono')) normalizedCategory = 'Fertilizers';
      else if (lowerCat.includes('machin') || lowerCat.includes('tractor') || lowerCat.includes('equip')) normalizedCategory = 'Machinery';
      else if (lowerCat.includes('suppl') || lowerCat.includes('office') || lowerCat.includes('tool')) normalizedCategory = 'Supplies';
      else normalizedCategory = category.trim();

      query = query.where('category', '==', normalizedCategory);
    }

    let snapshot = await query.limit(50).get();

    if (snapshot.empty) {
      // Fallback 1: check 'inventory' collection with category if applicable
      let invQuery: admin.firestore.Query = db.collection('inventory');
      if (normalizedCategory) {
        invQuery = invQuery.where('category', '==', normalizedCategory);
      }
      snapshot = await invQuery.limit(50).get();
    }

    if (snapshot.empty && normalizedCategory) {
      // Fallback 2: try querying without category filter from inventory_items
      const allSnapshot = await db.collection('inventory_items').limit(50).get();
      if (!allSnapshot.empty) {
        return processInventoryDocs(allSnapshot.docs, itemName);
      }
      // Fallback 3: try querying without category filter from inventory
      const allInvSnapshot = await db.collection('inventory').limit(50).get();
      if (!allInvSnapshot.empty) {
        return processInventoryDocs(allInvSnapshot.docs, itemName);
      }
    }

    return processInventoryDocs(snapshot.docs, itemName);
  } catch (error: any) {
    console.error('[firestoreTools] Error checking stock availability:', error);
    return {
      success: false,
      totalFound: 0,
      items: [],
      message: `Failed to query inventory: ${error?.message || 'Unknown database error'}`
    };
  }
}

function processInventoryDocs(
  docs: admin.firestore.QueryDocumentSnapshot[],
  itemNameFilter?: string
): { success: boolean; totalFound: number; items: InventoryItemResult[]; message?: string } {
  const items: InventoryItemResult[] = [];
  const queryTerm = itemNameFilter?.trim().toLowerCase() || '';

  for (const doc of docs) {
    const data = doc.data();
    const name: string = data.itemName || data.item_name || data.name || '';
    const cat: string = data.category || 'Supplies';
    const qty: number = typeof data.quantity === 'number' ? data.quantity : Number(data.quantity || 0);
    const unit: string = data.unit || 'units';

    // If itemName is provided, perform case-insensitive substring match
    if (queryTerm && !name.toLowerCase().includes(queryTerm) && !cat.toLowerCase().includes(queryTerm)) {
      continue;
    }

    // Determine status: Out of Stock (0), Low Stock (<10), In Stock (10+)
    const status: 'In Stock' | 'Low Stock' | 'Out of Stock' =
      qty <= 0 ? 'Out of Stock' : qty < 10 ? 'Low Stock' : 'In Stock';

    let updatedAtStr: string | undefined = undefined;
    const rawUpdated = data.updatedAt || data.updated_at;
    if (rawUpdated?.toDate) {
      updatedAtStr = rawUpdated.toDate().toISOString();
    } else if (rawUpdated) {
      updatedAtStr = String(rawUpdated);
    }

    items.push({
      id: doc.id,
      itemName: name,
      category: cat,
      quantity: qty,
      unit: unit,
      status: status,
      updatedAt: updatedAtStr
    });
  }

  return {
    success: true,
    totalFound: items.length,
    items: items,
    message: items.length === 0
      ? `No matching items found${itemNameFilter ? ` for "${itemNameFilter}"` : ''} in AgriServe inventory.`
      : undefined
  };
}

/**
 * 2. getRequestStatus
 * Fetches the document from `supply_requests`.
 * Crucial Security Rule: Enforces that `doc.data().userId === contextUid`.
 * If unauthorized, returns `{ error: "Access denied or request not found." }`.
 */
export async function getRequestStatus(
  params: GetRequestStatusParams,
  contextUid: string
): Promise<{ success: boolean; data?: any; error?: string }> {
  try {
    const { requestId } = params;
    if (!requestId || !requestId.trim()) {
      return {
        success: false,
        error: 'Please provide a valid Request ID.'
      };
    }

    const cleanRequestId = requestId.trim();
    let docSnap = await db.collection('supply_requests').doc(cleanRequestId).get();

    // Fallback: check 'requests' collection if not in supply_requests
    let collectionName = 'supply_requests';
    if (!docSnap.exists) {
      const altSnap = await db.collection('requests').doc(cleanRequestId).get();
      if (altSnap.exists) {
        docSnap = altSnap;
        collectionName = 'requests';
      }
    }

    if (!docSnap.exists) {
      // Also attempt searching by a custom requestId field if ID was stored as a field
      let querySnap = await db.collection('supply_requests')
        .where('requestId', '==', cleanRequestId)
        .limit(1)
        .get();

      if (querySnap.empty) {
        querySnap = await db.collection('requests')
          .where('requestId', '==', cleanRequestId)
          .limit(1)
          .get();
      }

      if (querySnap.empty) {
        return {
          success: false,
          error: 'Access denied or request not found.'
        };
      }

      const matchDoc = querySnap.docs[0];
      const matchData = matchDoc.data();

      // Security check: must match authenticated user's UID
      const docOwnerId = matchData.userId || matchData.user_id || matchData.farmerId;
      if (docOwnerId !== contextUid) {
        console.warn(`[firestoreTools] Unauthorized request access attempt by user ${contextUid} on doc ${matchDoc.id}`);
        return {
          success: false,
          error: 'Access denied or request not found.'
        };
      }

      return {
        success: true,
        data: formatRequestData(matchDoc.id, matchData)
      };
    }

    const data = docSnap.data();
    if (!data) {
      return {
        success: false,
        error: 'Access denied or request not found.'
      };
    }

    // Crucial Security Rule: Enforce that doc.data().userId === contextUid
    const ownerId = data.userId || data.user_id || data.farmerId;
    if (ownerId !== contextUid) {
      console.warn(`[firestoreTools] Unauthorized request access attempt by user ${contextUid} on doc ${docSnap.id}`);
      return {
        success: false,
        error: 'Access denied or request not found.'
      };
    }

    return {
      success: true,
      data: formatRequestData(docSnap.id, data)
    };
  } catch (error: any) {
    console.error('[firestoreTools] Error fetching request status:', error);
    return {
      success: false,
      error: 'Access denied or request not found.'
    };
  }
}

function formatRequestData(docId: string, data: admin.firestore.DocumentData) {
  let createdAtStr = '';
  const rawDate = data.createdAt || data.timestamp || data.requestedAt || data.created_at;
  if (rawDate?.toDate) {
    createdAtStr = rawDate.toDate().toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    });
  } else if (rawDate) {
    createdAtStr = String(rawDate);
  }

  const rawStatus = (data.status || '').toString().toLowerCase();
  let normalizedStatus = 'Pending';
  if (data.distributed === true || data.isDistributed === true || rawStatus === 'distributed' || rawStatus === 'completed') {
    normalizedStatus = 'In Distribution';
  } else if (data.approved === true || data.isApproved === true || rawStatus === 'approved' || rawStatus === 'accepted') {
    normalizedStatus = 'Approved';
  } else if (data.rejected === true || data.isRejected === true || rawStatus === 'rejected') {
    normalizedStatus = 'Rejected';
  } else if (data.status) {
    normalizedStatus = data.status;
  }

  return {
    requestId: docId,
    itemName: data.itemName || data.item_name || data.name || data.requestType || 'Agricultural Supply',
    quantityRequested: data.quantityRequested || data.quantity || 1,
    status: normalizedStatus,
    createdAt: createdAtStr,
    notes: data.notes || data.remarks || undefined
  };
}

/**
 * 3. escalateToStaff
 * Adds a document to `support_tickets` with the authenticated `userId`.
 */
export async function escalateToStaff(
  params: EscalateToStaffParams,
  contextUid: string
): Promise<{ success: boolean; ticketId?: string; message: string; error?: string }> {
  try {
    const { issueSummary, category } = params;

    if (!issueSummary || !issueSummary.trim()) {
      return {
        success: false,
        message: 'A summary of the issue is required to file a support ticket.'
      };
    }

    const ticketData = {
      userId: contextUid,
      issueSummary: issueSummary.trim(),
      category: category?.trim() || 'General Support',
      status: 'Open',
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      channel: 'AI Customer Service'
    };

    const docRef = await db.collection('support_tickets').add(ticketData);

    return {
      success: true,
      ticketId: docRef.id,
      message: `Support ticket #${docRef.id.substring(0, 7).toUpperCase()} has been submitted to the Department of Agriculture municipal technical team. A staff member will review and assist you promptly.`
    };
  } catch (error: any) {
    console.error('[firestoreTools] Error escalating ticket to staff:', error);
    return {
      success: false,
      message: `Failed to escalate support ticket: ${error?.message || 'Database error'}`
    };
  }
}

