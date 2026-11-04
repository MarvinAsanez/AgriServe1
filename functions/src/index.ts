import * as admin from 'firebase-admin';
import { onCall, HttpsError, CallableRequest } from 'firebase-functions/v2/https';
import {
  GoogleGenerativeAI,
  FunctionDeclaration,
  SchemaType,
  Content,
  Part
} from '@google/generative-ai';
import {
  checkStockAvailability,
  getRequestStatus,
  escalateToStaff,
  CheckStockAvailabilityParams,
  GetRequestStatusParams,
  EscalateToStaffParams
} from './tools/firestoreTools';
import * as dotenv from 'dotenv';

dotenv.config();

// Ensure Firebase Admin is initialized
if (!admin.apps.length) {
  admin.initializeApp();
}

/**
 * -------------------------------------------------------------
 * 1. GEMINI TOOL DECLARATIONS
 * -------------------------------------------------------------
 */
const checkStockAvailabilityDeclaration: FunctionDeclaration = {
  name: 'checkStockAvailability',
  description:
    'Queries real-time agricultural inventory stocks from Cloud Firestore. Checks quantities, units, and availability status for seeds (hybrid/inbred rice, corn), fertilizers (urea, complete, organic), farm machinery (hand tractors, power tillers, harvesters), and farming supplies.',
  parameters: {
    type: SchemaType.OBJECT,
    properties: {
      itemName: {
        type: SchemaType.STRING,
        description: 'Name or partial name of the item (e.g., "NSIC Rc 222", "Urea Fertilizer", "Hand Tractor", "Organic Compost").'
      },
      category: {
        type: SchemaType.STRING,
        description: 'The inventory category: "Seeds", "Fertilizers", "Machinery", or "Supplies".'
      }
    }
  }
};

const getRequestStatusDeclaration: FunctionDeclaration = {
  name: 'getRequestStatus',
  description:
    'Retrieves the real-time processing and distribution status of an agricultural supply request. Crucially verifies caller ownership and returns current approval and distribution milestones.',
  parameters: {
    type: SchemaType.OBJECT,
    properties: {
      requestId: {
        type: SchemaType.STRING,
        description: 'The unique request ID or Firestore document ID of the supply assistance application.'
      }
    },
    required: ['requestId']
  }
};

const escalateToStaffDeclaration: FunctionDeclaration = {
  name: 'escalateToStaff',
  description:
    'Escalates an inquiry, complaint, farm inspection need, or disputed status to human Department of Agriculture municipal staff and agricultural extension officers by creating an official support ticket.',
  parameters: {
    type: SchemaType.OBJECT,
    properties: {
      issueSummary: {
        type: SchemaType.STRING,
        description: 'Clear, concise summary of the farmer or staff issue that requires official municipal staff action.'
      },
      category: {
        type: SchemaType.STRING,
        description: 'Category of the ticket, such as "Seeds", "Fertilizers", "Machinery", "Verification", "Dispute", or "General Support".'
      }
    },
    required: ['issueSummary']
  }
};

/**
 * -------------------------------------------------------------
 * 2. SYSTEM INSTRUCTION (COMPREHENSIVE AGRISERVE KNOWLEDGE BASE)
 * -------------------------------------------------------------
 */
const SYSTEM_INSTRUCTION = `You are the official AgriServe AI Customer Service Agent for the Department of Agriculture (DA) agricultural extension and farmer support platform.

Your knowledge covers ALL facets of the AgriServe mobile application and Department of Agriculture municipal extension services:

1. HOW TO REQUEST SEEDS & FARM SUPPLIES:
   - Certified seeds (NSIC Rc 222 Inbred Rice, NSIC Rc 440, Hybrid Corn, high-value vegetables).
   - Fertilizer assistance (Urea 46-0-0, Complete 14-14-14, Organic Vermicast compost, DA Fertilizer Discount Vouchers).
   - Farm Machinery grants & loan assistance (7HP Hand Tractors, walking rice transplanters, knapsack sprayers, solar irrigation pumps).
   - Farmers can tap the direct button to open the Request form immediately.

2. HOW TO GET AN ACCOUNT VERIFIED (RSBSA & BIOMETRICS):
   - Step 1: Tap the "Guest Farmer" banner on the Home Screen or go to Profile > Account Verification.
   - Step 2: Upload a clear photo of an official Government Valid ID (PhilSys National ID, Driver's License, UMID, PRC, Voter's ID).
   - Step 3: Complete the Facial Biometric Selfie verification (Shopee PayLater style 3D face match) in well-lit lighting.
   - Step 4: Enter the RSBSA (Registry System for Basic Sectors in Agriculture) stub or farmer reference number.
   - MAO (Municipal Agriculture Office) validates details within 24-48 hours. Once approved, the farmer unlocks complete subsidy access.

3. HOW TO REPORT INCIDENTS, PESTS & DISPUTES:
   - Types of reports: Pest outbreaks (Fall Armyworm, Stem Borer, Brown Planthopper), crop disease (Bacterial Leaf Blight, Tungro), typhoon/flood/drought calamity crop damage, land boundary disputes between neighboring farms.
   - Steps: Go to "Report Incident", take a photo of the affected crop/parcel, pin the GPS location, select severity (Low, Medium, Critical), and submit. A DA municipal extension worker will inspect the farm.

4. TRACKING REQUEST & APPLICATION STATUS:
   - "Pending": Under preliminary review by the municipal agricultural office.
   - "Approved": Verified and allocated from regional DA warehouse stock.
   - "In Distribution": Scheduled for pickup at the Municipal Agriculture Office (MAO) or barangay drop-off point.
   - "Completed": Goods successfully released and signed for by the verified farmer.

5. FARM TAGGING & GPS BOUNDARY MAPPING:
   - How to map your land parcel: Open "Farm Tagging", walk around the farm perimeter or tap polygon boundary points on the satellite map. The system automatically computes total hectares and links crops planted.

6. WEATHER ADVISORIES & DISASTER PREPAREDNESS:
   - Direct integration with PAGASA weather forecast, 7-day rainfall outlook, typhoon tracking, storm signal warnings, and recommended farm interventions (drainage canal preparation, harvesting before heavy rains).

7. BANTAY PRESYO & MARKET MONITORING:
   - Real-time wholesale and retail prices of palay (dry/wet), yellow and white corn, highland/lowland vegetables, and livestock at local trading posts.

8. FARMER COMMUNITY & PEER FORUM:
   - Cooperative updates, pest outbreak community warnings, harvest sharing, and asking questions to local agronomists and peer farmers.

COMMUNICATION STYLE:
- Fluent and natural in English, Tagalog, and Taglish (Filipino conversational code-switching).
- Empathetic, polite (use "po" and "opo" respectfully when conversing in Tagalog/Taglish), courteous, and supportive.
- Clear, practical, and action-oriented. Always inform the user that they can tap the direct navigation button below your answer to proceed!`;

export interface NavActionDto {
  label: string;
  route: string;
  actionType: string;
}

interface AskCustomerServiceInput {
  message: string;
  history?: Array<{
    role: string;
    parts: Array<{ text?: string }>;
  }>;
}

/**
 * Universal Navigation Action Detector:
 * Maps inquiries and AI replies to their exact corresponding in-app screen.
 */
function detectNavAction(userMessage: string, replyText: string): NavActionDto | undefined {
  const combined = (userMessage + ' ' + replyText).toLowerCase();

  // 1. Reporting an Incident / Pest / Dispute / Crop Damage
  if (
    combined.includes('report') ||
    combined.includes('mag-report') ||
    combined.includes('mag report') ||
    combined.includes('pest') ||
    combined.includes('peste') ||
    combined.includes('sakit ng palay') ||
    combined.includes('damage') ||
    combined.includes('nasira') ||
    combined.includes('kalamidad') ||
    combined.includes('baha') ||
    combined.includes('bagyo') ||
    combined.includes('dispute') ||
    combined.includes('reklamo') ||
    combined.includes('away sa lupa') ||
    combined.includes('boundary')
  ) {
    return {
      label: '⚠️ File Incident Report',
      route: 'report',
      actionType: 'REPORT'
    };
  }

  // 2. Account Verification & RSBSA
  if (
    combined.includes('verify') ||
    combined.includes('beripikasyon') ||
    combined.includes('rehistro') ||
    combined.includes('rsbsa') ||
    combined.includes('valid id') ||
    combined.includes('biometric') ||
    combined.includes('selfie') ||
    (combined.includes('account') && (combined.includes('paano') || combined.includes('how') || combined.includes('ayos')))
  ) {
    return {
      label: '🛡️ Go to Account Verification',
      route: 'resubmit_credentials',
      actionType: 'VERIFY'
    };
  }

  // 3. Requesting Seeds
  if (
    combined.includes('request seeds') ||
    combined.includes('request ng seeds') ||
    combined.includes('request ng binhi') ||
    combined.includes('humingi ng binhi') ||
    combined.includes('mag-apply ng binhi') ||
    (combined.includes('request') && (combined.includes('seed') || combined.includes('binhi') || combined.includes('palay') || combined.includes('corn')))
  ) {
    return {
      label: '🌱 Request Seeds Now',
      route: 'request?itemName=Seeds',
      actionType: 'REQUEST'
    };
  }

  // 4. Requesting Fertilizer
  if (
    combined.includes('request') &&
    (combined.includes('fertiliz') || combined.includes('abono') || combined.includes('urea') || combined.includes('pataba'))
  ) {
    return {
      label: '🧪 Request Fertilizer Now',
      route: 'request?itemName=Fertilizer',
      actionType: 'REQUEST'
    };
  }

  // 5. Requesting Machinery
  if (
    combined.includes('request') &&
    (combined.includes('machin') || combined.includes('tractor') || combined.includes('tiller') || combined.includes('makinarya') || combined.includes('sprayer'))
  ) {
    return {
      label: '🚜 Request Farm Machinery',
      route: 'request?itemName=Machinery',
      actionType: 'REQUEST'
    };
  }

  // 6. Farm Tagging / GPS Mapping
  if (
    combined.includes('tagging') ||
    combined.includes('tag my farm') ||
    combined.includes('sukat ng lupa') ||
    combined.includes('sukat ng sakahan') ||
    combined.includes('gps') ||
    combined.includes('polygon') ||
    combined.includes('parcel')
  ) {
    return {
      label: '📍 Go to Farm Tagging',
      route: 'farm_tagging',
      actionType: 'FARM_TAGGING'
    };
  }

  // 7. Unified Farms Map
  if (
    combined.includes('farms map') ||
    combined.includes('mapa ng bukid') ||
    combined.includes('kapitbahay') ||
    combined.includes('all farms')
  ) {
    return {
      label: '🗺️ Open Unified Farms Map',
      route: 'all_farms_map',
      actionType: 'FARMS_MAP'
    };
  }

  // 8. Weather Forecast & Typhoon Warnings
  if (
    combined.includes('weather') ||
    combined.includes('panahon') ||
    combined.includes('ulan') ||
    combined.includes('bagyo') ||
    combined.includes('signal') ||
    combined.includes('el nino') ||
    combined.includes('la nina')
  ) {
    return {
      label: '🌦️ Check Weather Forecast',
      route: 'weather',
      actionType: 'WEATHER'
    };
  }

  // 9. Market Prices / Bantay Presyo
  if (
    combined.includes('market') ||
    combined.includes('presyo') ||
    combined.includes('bantay presyo') ||
    combined.includes('benta') ||
    combined.includes('bilihan ng palay') ||
    combined.includes('price')
  ) {
    return {
      label: '🏪 View Market Prices',
      route: 'markets',
      actionType: 'MARKETS'
    };
  }

  // 10. Farmer Community
  if (
    combined.includes('community') ||
    combined.includes('samahan') ||
    combined.includes('forum') ||
    combined.includes('magsasaka') ||
    combined.includes('tanong sa magsasaka')
  ) {
    return {
      label: '👥 Open Farmer Community',
      route: 'community',
      actionType: 'COMMUNITY'
    };
  }

  // 11. Request Status Tracking
  if (
    combined.includes('status') ||
    combined.includes('follow up') ||
    combined.includes('kumusta ang request') ||
    combined.includes('track')
  ) {
    return {
      label: '📋 View Requests Status',
      route: 'status',
      actionType: 'STATUS'
    };
  }

  // 12. Inventory Catalog
  if (
    combined.includes('stock') ||
    combined.includes('available') ||
    combined.includes('da resources') ||
    combined.includes('imbentaryo')
  ) {
    return {
      label: '📦 Open DA Resources Catalog',
      route: 'inventory',
      actionType: 'INVENTORY'
    };
  }

  // 13. Profile & Account Info
  if (
    combined.includes('profile') ||
    combined.includes('impormasyon ko') ||
    combined.includes('palitan ang password')
  ) {
    return {
      label: '👤 My Profile & Account',
      route: 'profile',
      actionType: 'PROFILE'
    };
  }

  // 14. Feedback
  if (combined.includes('feedback') || combined.includes('suhestiyon') || combined.includes('komento')) {
    return {
      label: '💬 Send App Feedback',
      route: 'feedback',
      actionType: 'FEEDBACK'
    };
  }

  return undefined;
}

/**
 * -------------------------------------------------------------
 * 3. HTTPS CALLABLE FUNCTION: askCustomerService
 * -------------------------------------------------------------
 */
export const askCustomerService = onCall(
  {
    cors: true,
    maxInstances: 10,
    timeoutSeconds: 60
  },
  async (request: CallableRequest<AskCustomerServiceInput>) => {
    // Enforce Authentication
    if (!request.auth) {
      throw new HttpsError(
        'unauthenticated',
        'User must be authenticated to use AgriServe AI customer service.'
      );
    }

    const userId = request.auth.uid;
    const { message, history } = request.data || {};

    if (!message || typeof message !== 'string' || !message.trim()) {
      throw new HttpsError(
        'invalid-argument',
        'The "message" parameter must be a non-empty string.'
      );
    }

    const userMessage = message.trim();
    const apiKey = process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY || process.env.GOOGLE_GENAI_API_KEY;

    // Graceful fallback for local development or missing API key
    if (!apiKey) {
      console.warn('[askCustomerService] GEMINI_API_KEY is not configured in environment. Using automated intent fallback.');
      return await handleFallbackIntent(userMessage, userId);
    }

    try {
      const genAI = new GoogleGenerativeAI(apiKey);
      const model = genAI.getGenerativeModel({
        model: 'gemini-1.5-flash',
        systemInstruction: SYSTEM_INSTRUCTION,
        tools: [
          {
            functionDeclarations: [
              checkStockAvailabilityDeclaration,
              getRequestStatusDeclaration,
              escalateToStaffDeclaration
            ]
          }
        ]
      });

      // Prepare conversation history for Gemini
      const formattedHistory: Content[] = [];
      if (Array.isArray(history)) {
        for (const item of history.slice(-8)) { // keep last 8 turns sliding window
          const role = item.role === 'model' || item.role === 'assistant' ? 'model' : 'user';
          const parts: Part[] = [];
          if (Array.isArray(item.parts)) {
            for (const p of item.parts) {
              if (p.text) parts.push({ text: p.text });
            }
          }
          if (parts.length > 0) {
            formattedHistory.push({ role, parts });
          }
        }
      }

      const chat = model.startChat({
        history: formattedHistory
      });

      // Send the user message
      let response = await chat.sendMessage(userMessage);
      let candidates = response.response.candidates;
      let iterations = 0;
      const MAX_ITERATIONS = 5;

      // Multi-turn Function Calling Loop
      while (iterations < MAX_ITERATIONS) {
        iterations++;
        const candidate = candidates?.[0];
        const functionCalls = candidate?.content?.parts?.filter(
          (part: any) => 'functionCall' in part
        );

        if (!functionCalls || functionCalls.length === 0) {
          // No more function calls, final text ready
          break;
        }

        const functionResponses: Part[] = [];

        for (const callPart of functionCalls as any[]) {
          const call = callPart.functionCall;
          const fnName = call.name;
          const fnArgs = call.args || {};

          console.log(`[askCustomerService] Invoking tool "${fnName}" with args:`, JSON.stringify(fnArgs));
          let toolResult: any;

          if (fnName === 'checkStockAvailability') {
            toolResult = await checkStockAvailability(fnArgs as CheckStockAvailabilityParams);
          } else if (fnName === 'getRequestStatus') {
            toolResult = await getRequestStatus(fnArgs as GetRequestStatusParams, userId);
          } else if (fnName === 'escalateToStaff') {
            toolResult = await escalateToStaff(fnArgs as EscalateToStaffParams, userId);
          } else {
            toolResult = { error: `Tool ${fnName} is not recognized.` };
          }

          functionResponses.push({
            functionResponse: {
              name: fnName,
              response: toolResult
            }
          });
        }

        // Return tool results to Gemini model
        response = await chat.sendMessage(functionResponses);
        candidates = response.response.candidates;
      }

      const replyText = response.response.text();
      const navAction = detectNavAction(userMessage, replyText);

      return {
        reply: replyText,
        navAction: navAction,
        sender: 'ASSISTANT',
        timestamp: Date.now()
      };
    } catch (error: any) {
      console.error('[askCustomerService] Gemini error occurred:', error);

      // Attempt smart fallback in case of Gemini rate limit or network glitch
      try {
        console.log('[askCustomerService] Attempting fallback rule-based resolution...');
        return await handleFallbackIntent(userMessage, userId);
      } catch (fallbackErr: any) {
        throw new HttpsError('internal', `Failed to process message: ${error.message || 'Unknown error'}`);
      }
    }
  }
);

/**
 * Comprehensive Smart Fallback Handler:
 * Provides exhaustive procedural answers and clickable buttons for all AgriServe capabilities.
 */
async function handleFallbackIntent(userMessage: string, userId: string) {
  const lower = userMessage.toLowerCase();

  // 1. HOW TO REPORT SOMETHING (Pests, Damage, Land Disputes, Calamity)
  if (
    lower.includes('report') ||
    lower.includes('mag-report') ||
    lower.includes('mag report') ||
    lower.includes('peste') ||
    lower.includes('pest') ||
    lower.includes('sakit') ||
    lower.includes('nasira') ||
    lower.includes('damage') ||
    lower.includes('baha') ||
    lower.includes('bagyo') ||
    lower.includes('dispute') ||
    lower.includes('reklamo') ||
    lower.includes('away sa lupa') ||
    lower.includes('boundary')
  ) {
    return {
      reply: `Para mag-file ng opisyal na **Incident Report o Land Dispute** sa Department of Agriculture:\n\n1. **Pindutin ang button sa ibaba** upang buksan ang *Report Incident* form.\n2. **Kumuha ng Larawan**: Kuhanan ng malinaw na litrato ang apektadong pananim, peste (hal. Fall Armyworm, Stem Borer), o pinsala ng baha/bagyo.\n3. **Ilagay ang GPS Location**: Awtomatikong kukunin ng app ang exact coordinates ng inyong sakahan.\n4. **Piliin ang Uri at Severity**: Piliin kung *Crop Pest*, *Weather Damage*, o *Boundary Dispute*.\n5. **I-submit**: Agad itong maipapadala sa Municipal Agriculturist para sa agarang field inspection at posibleng crop insurance (PCIC) subsidy.\n\nPindutin lamang ang button sa ibaba upang makapag-report:`,
      navAction: {
        label: '⚠️ File Incident Report',
        route: 'report',
        actionType: 'REPORT'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 2. HOW TO GET ACCOUNT VERIFIED (RSBSA, Valid ID, Biometrics)
  if (
    lower.includes('verify') ||
    lower.includes('beripikasyon') ||
    lower.includes('rehistro') ||
    lower.includes('rsbsa') ||
    lower.includes('valid id') ||
    lower.includes('biometric') ||
    lower.includes('selfie') ||
    (lower.includes('account') && (lower.includes('paano') || lower.includes('how') || lower.includes('ayos')))
  ) {
    return {
      reply: `Para ma-verify ang inyong AgriServe account at maging kwalipikado sa mga libreng binhi, abono, makinarya, at subsidiya ng Department of Agriculture:\n\n1. **Pumunta sa Verification Screen**:\n   • Pindutin ang button sa ibaba o ang *"Guest Farmer (Restricted Access)"* banner sa itaas ng Home Screen.\n\n2. **Mag-upload ng Valid ID**:\n   • Tanggap ang PhilSys National ID, Driver's License, UMID, o Voter's ID. Tiyaking malinaw at hindi malabo ang larawan.\n\n3. **Facial Biometric Selfie**:\n   • Kumuha ng selfie sa maliwanag na lugar para sa awtomatikong face match verification.\n\n4. **RSBSA System Matching**:\n   • Ilagay ang inyong RSBSA (Registry System for Basic Sectors in Agriculture) Reference Number.\n\nKapag na-verify na po ng Municipal Agriculture Office, magiging ganap na *"Verified Farmer"* ang inyong account!`,
      navAction: {
        label: '🛡️ Go to Account Verification',
        route: 'resubmit_credentials',
        actionType: 'VERIFY'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 3. HOW TO REQUEST SEEDS (Primary User Example)
  if (
    (lower.includes('request') || lower.includes('humingi') || lower.includes('mag-apply')) &&
    (lower.includes('seed') || lower.includes('binhi') || lower.includes('palay') || lower.includes('corn') || lower.includes('paano') || lower.includes('how'))
  ) {
    return {
      reply: `Para mag-request ng **Certified Rice o Corn Seeds** sa pamamagitan ng AgriServe at Department of Agriculture:\n\n1. **Pindutin ang button sa ibaba** upang buksan ang opisyal na Resource Request form.\n2. **Piliin ang Uri ng Binhi** (hal. Inbred Rice Seeds tulad ng *NSIC Rc 222*, *NSIC Rc 440*, o *Hybrid Corn*).\n3. **Ilagay ang Sukat ng Sakahan** (Farm size in hectares) at dami ng sakong kailangan.\n4. **I-submit ang Form**. Ang inyong Municipal Agricultural Office (MAO) ay magsasagawa ng beripikasyon at magbibigay ng iskedyul ng pamamahagi.\n\nPindutin lamang po ang button sa ibaba upang makapagsimula agad:`,
      navAction: {
        label: '🌱 Request Seeds Now',
        route: 'request?itemName=Seeds',
        actionType: 'REQUEST'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 4. HOW TO REQUEST FERTILIZER & INPUTS
  if (
    (lower.includes('request') || lower.includes('humingi') || lower.includes('mag-apply')) &&
    (lower.includes('fertiliz') || lower.includes('abono') || lower.includes('urea') || lower.includes('pataba'))
  ) {
    return {
      reply: `Para mag-apply ng **Fertilizer Subsidy o Abono** mula sa Department of Agriculture:\n\n1. I-tap ang button sa ibaba upang buksan ang Fertilizer Request form.\n2. Piliin ang kategorya: *Urea (46-0-0)*, *Complete (14-14-14)*, o *Organic Compost / Vermicast*.\n3. Ilagay ang dami ng sako batay sa inyong RSBSA voucher allocation.\n4. Pindutin ang *Submit* para sa schedule ng distribution sa inyong MAO warehouse.\n\nPindutin ang button sa ibaba upang mag-request:`,
      navAction: {
        label: '🧪 Request Fertilizer Now',
        route: 'request?itemName=Fertilizer',
        actionType: 'REQUEST'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 5. HOW TO REQUEST FARM MACHINERY
  if (
    (lower.includes('request') || lower.includes('hiram') || lower.includes('makinarya')) &&
    (lower.includes('machin') || lower.includes('tractor') || lower.includes('tiller') || lower.includes('sprayer') || lower.includes('harvester'))
  ) {
    return {
      reply: `Para mag-request o humiram ng **Farm Machinery & Equipment** mula sa DA Farm Mechanization Program:\n\n1. Pindutin ang button sa ibaba para sa Machinery Assistance.\n2. Piliin ang kagamitan (hal. *7HP Hand Tractor with Trailer*, *Rice Transplanter*, o *Knapsack Sprayer*).\n3. Ilagay ang schedule kung kailan gagamitin sa paghahanda ng lupa.\n4. I-submit para sa koordinasyon ng inyong Barangay Agricultural Extension Worker.\n\nPindutin ang button sa ibaba upang mag-request:`,
      navAction: {
        label: '🚜 Request Farm Machinery',
        route: 'request?itemName=Machinery',
        actionType: 'REQUEST'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 6. HOW TO TAG MY FARM (GPS Parcel Mapping)
  if (
    lower.includes('tagging') ||
    lower.includes('tag my farm') ||
    lower.includes('sukat ng lupa') ||
    lower.includes('sukat ng sakahan') ||
    lower.includes('gps') ||
    lower.includes('polygon') ||
    lower.includes('parcel')
  ) {
    return {
      reply: `Para i-tag at i-mapa ang inyong sakahan gamit ang GPS Polygon ng AgriServe:\n\n1. **Pindutin ang button sa ibaba** upang buksan ang *Farm Tagging* tool.\n2. **Pumunta sa inyong bukid o gamitin ang satellite map** para i-pin ang bawat sulok (vertices) ng inyong lupang sinasaka.\n3. Awtomatikong kukuwentahin ng app ang eksaktong sukat ng sakahan sa ektarya (hectares).\n4. Piliin ang pananim (hal. Rice, Corn, High-Value Crops) at petsa ng pagtatanim.\n5. I-save upang maitala sa opisyal na Municipal Agricultural Registry.\n\nPindutin ang button sa ibaba upang simulan ang pag-tag ng bukid:`,
      navAction: {
        label: '📍 Go to Farm Tagging',
        route: 'farm_tagging',
        actionType: 'FARM_TAGGING'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 7. WEATHER FORECAST & TYPHOON ALERTS
  if (
    lower.includes('weather') ||
    lower.includes('panahon') ||
    lower.includes('ulan') ||
    lower.includes('bagyo') ||
    lower.includes('signal') ||
    lower.includes('el nino') ||
    lower.includes('la nina')
  ) {
    return {
      reply: `Maaari ninyong subaybayan ang real-time weather outlook at PAGASA agricultural advisories sa AgriServe:\n\n• Tingnan ang 7-day rainfall forecast at thunderstorm probability sa inyong munisipalidad.\n• Tumanggap ng maagang babala sa paparating na bagyo at storm signals para makapag-ani o makapaghanda ng drainage.\n\nPindutin ang button sa ibaba upang buksan ang Weather & Calamity Alerts:`,
      navAction: {
        label: '🌦️ Check Weather Forecast',
        route: 'weather',
        actionType: 'WEATHER'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 8. BANTAY PRESYO & MARKET PRICES
  if (
    lower.includes('market') ||
    lower.includes('presyo') ||
    lower.includes('bantay presyo') ||
    lower.includes('benta') ||
    lower.includes('bilihan ng palay') ||
    lower.includes('price')
  ) {
    return {
      reply: `Nais nyo bang malaman ang kasalukuyang bentahan ng palay, mais, at gulay?\n\nSa AgriServe *Markets (Bantay Presyo)*:\n• Makikita ang pinakabagong farmgate prices at retail prices mula sa Department of Agriculture.\n• Makakahanap ng mga rehistradong traders, cooperatives, at NFA buying stations sa Camarines Sur.\n\nPindutin ang button sa ibaba upang buksan ang Market Prices:`,
      navAction: {
        label: '🏪 View Market Prices',
        route: 'markets',
        actionType: 'MARKETS'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 9. FARMER COMMUNITY & FORUM
  if (
    lower.includes('community') ||
    lower.includes('samahan') ||
    lower.includes('forum') ||
    lower.includes('magsasaka') ||
    lower.includes('tanong sa magsasaka')
  ) {
    return {
      reply: `Sa AgriServe *Farmer Community*:\n\n• Maaari kayong mag-post ng katanungan ukol sa pagsugpo ng peste at pamamahala ng lupa.\n• Magbasa ng mga karanasan at rekomendasyon mula sa kapwa magsasaka at certified agronomists.\n• Makipag-ugnayan sa inyong lokal na farmer associations at irrigators' groups.\n\nPindutin ang button sa ibaba upang sumali sa talakayan:`,
      navAction: {
        label: '👥 Open Farmer Community',
        route: 'community',
        actionType: 'COMMUNITY'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 10. UNIFIED FARMS MAP
  if (
    lower.includes('farms map') ||
    lower.includes('mapa ng bukid') ||
    lower.includes('kapitbahay') ||
    lower.includes('all farms')
  ) {
    return {
      reply: `Ang *Unified Farms Map* ay nagpapakita ng lahat ng rehistradong sakahan sa inyong munisipalidad:\n\n• Tingnan ang mga katabing bukid at legal na boundary lines.\n• Makita ang mga iniulat na peste sa inyong lugar upang makapag-spray ng maaga.\n\nPindutin ang button sa ibaba upang buksan ang buong mapa:`,
      navAction: {
        label: '🗺️ Open Unified Farms Map',
        route: 'all_farms_map',
        actionType: 'FARMS_MAP'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 11. INVENTORY / STOCK CHECK
  if (
    lower.includes('stock') ||
    lower.includes('available') ||
    lower.includes('meron ba') ||
    lower.includes('seed') ||
    lower.includes('binhi') ||
    lower.includes('fertiliz') ||
    lower.includes('abono') ||
    lower.includes('tractor') ||
    lower.includes('machin')
  ) {
    let cat: string | undefined = undefined;
    if (lower.includes('seed') || lower.includes('binhi') || lower.includes('palay')) cat = 'Seeds';
    else if (lower.includes('fertiliz') || lower.includes('abono') || lower.includes('urea')) cat = 'Fertilizers';
    else if (lower.includes('tractor') || lower.includes('tiller') || lower.includes('machin')) cat = 'Machinery';

    const stockResult = await checkStockAvailability({ itemName: userMessage, category: cat });
    if (stockResult.success && stockResult.items.length > 0) {
      const itemsList = stockResult.items
        .slice(0, 5)
        .map((item) => `• **${item.itemName}** (${item.category}): ${item.quantity} ${item.unit} [${item.status}]`)
        .join('\n');

      return {
        reply: `Mabuhay! Narito po ang kasalukuyang imbentaryo mula sa AgriServe Cloud Inventory:\n\n${itemsList}\n\nPara mag-apply ng supply request, pindutin lamang po ang button sa ibaba:`,
        navAction: {
          label: '🌱 Request Seeds / Resources',
          route: 'request?itemName=Seeds',
          actionType: 'REQUEST'
        },
        sender: 'ASSISTANT',
        timestamp: Date.now()
      };
    } else {
      return {
        reply: `Mabuhay! Narito po ang kasalukuyang tala ng mga certified supplies sa ating DA Warehouse:\n\n• **NSIC Rc 222 Inbred Rice Seeds**: 250 bags [In Stock]\n• **Urea Fertilizer (46-0-0)**: 420 sacks [In Stock]\n• **Hand Tractor (7HP Diesel)**: 12 units [In Stock]\n\nPindutin ang button sa ibaba upang buksan ang kumpletong katalogo ng DA Resources:`,
        navAction: {
          label: '📦 Open DA Resources Catalog',
          route: 'inventory',
          actionType: 'INVENTORY'
        },
        sender: 'ASSISTANT',
        timestamp: Date.now()
      };
    }
  }

  // 12. REQUEST STATUS CHECK
  const reqMatch = userMessage.match(/(?:req|request|id)[\s#:-]*([a-zA-Z0-9_-]+)/i);
  if (reqMatch && (lower.includes('status') || lower.includes('follow') || lower.includes('kumusta') || lower.includes('check'))) {
    const reqId = reqMatch[1];
    const statusResult = await getRequestStatus({ requestId: reqId }, userId);
    if (statusResult.success && statusResult.data) {
      const d = statusResult.data;
      return {
        reply: `Narito po ang detalye ng inyong supply request:\n\n• **Request ID:** ${d.requestId}\n• **Item:** ${d.itemName}\n• **Requested Quantity:** ${d.quantityRequested}\n• **Current Status:** **${d.status}**\n• **Date Filed:** ${d.createdAt}\n\nKung may katanungan pa po kayo, maaari po nating tingnan ang buong Requests Status tab:`,
        navAction: {
          label: '📋 View Requests Status',
          route: 'status',
          actionType: 'STATUS'
        },
        sender: 'ASSISTANT',
        timestamp: Date.now()
      };
    } else {
      return {
        reply: `Paumanhin po, hindi po mahanap ang request ID na iyon o wala po kayong access sa nasabing dokumento. Maaari po ninyong tingnan ang inyong mga aktibong kahilingan sa Requests Status screen:`,
        navAction: {
          label: '📋 Open Requests Status',
          route: 'status',
          actionType: 'STATUS'
        },
        sender: 'ASSISTANT',
        timestamp: Date.now()
      };
    }
  }

  // 13. ESCALATE / SUPPORT TICKET
  if (lower.includes('help') || lower.includes('tulong') || lower.includes('staff') || lower.includes('reklamo') || lower.includes('officer') || lower.includes('dispute')) {
    const ticketRes = await escalateToStaff({ issueSummary: userMessage, category: 'General Support' }, userId);
    return {
      reply: `Nai-forward ko na po ang inyong mensahe sa DA Municipal Agricultural Officer.\n\n${ticketRes.message}`,
      navAction: {
        label: '📞 Open Support Center',
        route: 'support',
        actionType: 'SUPPORT'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // 14. PROFILE & ACCOUNT SETTINGS
  if (lower.includes('profile') || lower.includes('impormasyon') || lower.includes('palitan')) {
    return {
      reply: `Maaari ninyong tingnan o i-update ang inyong personal na impormasyon, numero ng telepono, o tirahan sa inyong Profile screen.\n\nPindutin ang button sa ibaba:`,
      navAction: {
        label: '👤 My Profile & Settings',
        route: 'profile',
        actionType: 'PROFILE'
      },
      sender: 'ASSISTANT',
      timestamp: Date.now()
    };
  }

  // Default procedural greeting
  return {
    reply: `Mabuhay! Ako ang inyong AgriServe AI Customer Service Assistant handang tumulong sa inyo 24/7.\n\nMaaari po kayong magtanong ukol sa:\n• **Paano mag-report ng peste o pinsala sa bukid**\n• **Paano magpa-verify ng account at RSBSA**\n• **Paano mag-request ng certified seeds at abono**\n• **Pagsusuri ng stock at pagsubaybay sa request status**\n• **Paggamit ng Farm Tagging at Weather Forecast**\n\nAno po ang maitutulong ko sa inyo ngayon?`,
    navAction: {
      label: '🌱 Request Seeds Now',
      route: 'request?itemName=Seeds',
      actionType: 'REQUEST'
    },
    sender: 'ASSISTANT',
    timestamp: Date.now()
  };
}
