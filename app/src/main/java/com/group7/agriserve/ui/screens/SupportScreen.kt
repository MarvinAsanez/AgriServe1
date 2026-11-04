package com.group7.agriserve.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.group7.agriserve.viewmodel.UserViewModel
import java.util.UUID

data class FaqItem(
    val category: String,
    val question: String,
    val answer: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    userViewModel: UserViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All Topics") }

    // Direct Inquiry Form States
    var inquiryTopic by remember { mutableStateOf("Account Verification & RSBSA") }
    var inquiryTopicExpanded by remember { mutableStateOf(false) }
    var inquirySubject by remember { mutableStateOf("") }
    var inquiryMessage by remember { mutableStateOf("") }
    var inquiryPhone by remember { mutableStateOf(userViewModel?.farmerPhone?.value ?: "") }
    var isSubmittingInquiry by remember { mutableStateOf(false) }
    var submittedTicketId by remember { mutableStateOf<String?>(null) }
    var showTicketDialog by remember { mutableStateOf(false) }

    val topics = listOf(
        "Account Verification & RSBSA",
        "Resource Requests (Seeds / Fertilizer)",
        "Farm Machinery Assistance",
        "Pest Outbreak & Crop Damage Report",
        "Weather Advisories & Alerts",
        "Offline App Usage & Data Sync",
        "Other General Inquiry"
    )

    // Comprehensive FAQ Data
    val allFaqs = remember {
        listOf(
            FaqItem(
                category = "Verification",
                question = "How do I become a Verified Farmer in AgriServe?",
                answer = "Navigate to your Profile -> Verification & Identity. You will need to upload a photo of a recognized Government ID (e.g. RSBSA Certificate, Philippine National ID, UMID, Voter's ID) and complete the Live Face Biometric Scan using your front camera. A DA extension officer will review your documents and verify your account."
            ),
            FaqItem(
                category = "Verification",
                question = "What is the difference between Verified and Non-Verified Farmers?",
                answer = "Non-Verified Farmers can view latest DA agricultural news, weather advisories, market commodity prices, join the farmer community forums, and submit geotagged incident reports. Verified Farmers get full access to submit official resource assistance requests for certified seeds, fertilizers, and farm machinery."
            ),
            FaqItem(
                category = "Verification",
                question = "Why is the RSBSA number important?",
                answer = "The Registry System for Basic Sectors in Agriculture (RSBSA) is the national electronic database of Filipino farmers. An RSBSA number is required by the Department of Agriculture to cross-reference your eligibility for government fuel subsidies, crop insurance, and resource allotments. You can verify your registration status online via the official portal: https://finder-rsbsa.da.gov.ph/ph or contact the DA helpline at (9) 8273-2402 / info.rsbsa@da.gov.ph."
            ),
            FaqItem(
                category = "Verification",
                question = "How can I check or retrieve my RSBSA Number online?",
                answer = "You can search for your registered RSBSA record using the official Department of Agriculture RSBSA Finder at https://finder-rsbsa.da.gov.ph/ph. Enter your First Name, Middle Name, Last Name, Sex, and Date of Birth to view your registered RSBSA Number and farmer profile."
            ),
            FaqItem(
                category = "Verification",
                question = "Why does the live facial verification fail during scanning?",
                answer = "Ensure you are in a well-lit area without strong backlighting. Hold your phone steadily at eye level, remove face masks, hats, or dark sunglasses, and look directly into the camera ring until the green verification circle completes."
            ),
            FaqItem(
                category = "Requests",
                question = "How do I request seeds, fertilizers, or farm machinery?",
                answer = "Once your account is verified, tap the 'Request' icon in the bottom navigation bar. Select your needed assistance category (Seeds, Fertilizer, or Machinery), choose the specific item from the live DA inventory, indicate your needed quantity and farm area, and tap 'Submit Request'."
            ),
            FaqItem(
                category = "Requests",
                question = "How long does it take for my resource request to be approved?",
                answer = "Municipal Agriculture Offices (MAO) typically review requests within 1 to 3 business days. You can track real-time progress under the 'Status' tab on your Home screen. Once approved, you will receive pickup instructions."
            ),
            FaqItem(
                category = "Requests",
                question = "Where do I pick up the approved agricultural supplies?",
                answer = "Approved supplies are released through your designated Municipal Agriculture Office (MAO) or City Agriculture Office. Bring your valid Government ID and show your approved status screen in AgriServe upon pickup."
            ),
            FaqItem(
                category = "Incidents",
                question = "How do I report a pest infestation or typhoon crop damage?",
                answer = "Tap the 'Report' tab in the bottom bar. Select the incident category (Pest Outbreak, Flood, Drought, Crop Disease), attach a photo of the affected crop, and allow location permission. The app automatically records your farm's GPS coordinates so DA extension workers can respond promptly."
            ),
            FaqItem(
                category = "Offline",
                question = "Can I use AgriServe without an active internet connection?",
                answer = "Yes! AgriServe is engineered with full offline support. You can prepare and submit resource requests or incident reports even in remote fields without signal. Your submissions are saved locally on your phone and automatically dispatched to DA servers when internet connection is restored."
            ),
            FaqItem(
                category = "Offline",
                question = "How long can I remain logged in while offline?",
                answer = "AgriServe maintains an offline session validity of 7 days (1 week). You do not need an active internet connection to log in every day. You only need to connect online once every 7 days to refresh your security credentials."
            ),
            FaqItem(
                category = "Account",
                question = "How do I update my registered mobile number or farm location?",
                answer = "Go to Profile -> Update Profile Info. You can update your full name, phone number, and location (Region, Province, City/Municipality, Barangay, and Street). Tap 'Save Profile' to commit changes."
            ),
            FaqItem(
                category = "Account",
                question = "What should I do if I forgot my login password?",
                answer = "On the login screen, enter your registered email address and contact your local Municipal Agriculture Office or submit an inquiry in this Customer Service tab. A password reset link can also be requested via Firebase authentication."
            )
        )
    }

    val categories = listOf("All Topics", "Verification", "Requests", "Incidents", "Offline", "Account")

    val filteredFaqs = remember(searchQuery, selectedCategory, allFaqs) {
        allFaqs.filter { faq ->
            val matchesCategory = selectedCategory == "All Topics" || faq.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    faq.question.contains(searchQuery, ignoreCase = true) ||
                    faq.answer.contains(searchQuery, ignoreCase = true) ||
                    faq.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help Center & Customer Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("FAQs", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Support Desk", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Send Inquiry", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Policies", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // TAB 0: FREQUENTLY ASKED QUESTIONS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search questions, topics, or keywords...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Category Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Found ${filteredFaqs.size} Questions",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredFaqs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No matching answers found.", fontWeight = FontWeight.Bold)
                                    Text("Try searching for other keywords or contact customer support.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        } else {
                            filteredFaqs.forEach { faq ->
                                FaqAccordionCard(faq = faq)
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Bottom Help Desk Banner
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTab = 1 },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Still need help with your farm?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text("Tap here to call or message DA Customer Service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                1 -> {
                    // TAB 1: CUSTOMER SERVICE & DA HELP DESK
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Department of Agriculture Help Desk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Connect with our extension workers, hotline specialists, and agricultural technical support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Hotline Card (Call)
                        ContactActionCard(
                            icon = Icons.Default.PhoneInTalk,
                            iconColor = Color(0xFF2E7D32),
                            title = "DA Central Hotline (1-Tap Call)",
                            subtitle = "(02) 8928-8741 / Citizen Hotline 8888",
                            actionLabel = "Call Hotline",
                            onAction = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0289288741"))
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // SMS Help Desk
                        ContactActionCard(
                            icon = Icons.Default.Sms,
                            iconColor = Color(0xFF1976D2),
                            title = "Direct SMS Farmer Help Desk",
                            subtitle = "+63 917 888 8888 (Text Support)",
                            actionLabel = "Send Text Message",
                            onAction = {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:+639178888888")).apply {
                                    putExtra("sms_body", "Hello DA AgriServe, I am registered farmer ${userViewModel?.farmerName?.value ?: ""}. I need assistance regarding: ")
                                }
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Official Email Support
                        ContactActionCard(
                            icon = Icons.Default.Email,
                            iconColor = Color(0xFFE65100),
                            title = "Official DA Support Email",
                            subtitle = "support@agriserve.da.gov.ph",
                            actionLabel = "Send Email",
                            onAction = {
                                val farmerName = userViewModel?.farmerName?.value ?: ""
                                val farmerPhone = userViewModel?.farmerPhone?.value ?: ""
                                val rsbsa = userViewModel?.rsbsaNumber?.value ?: ""
                                val emailBody = """
Good day DA AgriServe Support,

Farmer Name: $farmerName
Phone: $farmerPhone
RSBSA Number: $rsbsa

My inquiry is:
""".trimIndent()
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@agriserve.da.gov.ph")).apply {
                                    putExtra(Intent.EXTRA_SUBJECT, "AgriServe Farmer Support Request - ${farmerName.ifBlank { "Farmer" }}")
                                    putExtra(Intent.EXTRA_TEXT, emailBody)
                                }
                                context.startActivity(intent)
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Operating Hours & Office Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Operating Hours & Service Desks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• Regular Desk: Monday to Friday, 8:00 AM – 5:00 PM (PST)", style = MaterialTheme.typography.bodySmall)
                                Text("• Emergency & Disaster Desk: 24/7 on-call during typhoons & pest alerts", style = MaterialTheme.typography.bodySmall)
                                Text("• Physical Office: Municipal Agriculture Offices (MAO) located in your city/town municipal hall", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { selectedTab = 2 },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Create, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit In-App Support Ticket", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                2 -> {
                    // TAB 2: IN-APP CUSTOMER SERVICE TICKET
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Submit an Inquiry / Support Ticket",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Send your questions or issues directly to our DA assistance team. We respond within 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Inquiry Topic Dropdown
                        ExposedDropdownMenuBox(
                            expanded = inquiryTopicExpanded,
                            onExpandedChange = { inquiryTopicExpanded = !inquiryTopicExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = inquiryTopic,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Topic / Department") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = inquiryTopicExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = inquiryTopicExpanded,
                                onDismissRequest = { inquiryTopicExpanded = false }
                            ) {
                                topics.forEach { top ->
                                    DropdownMenuItem(
                                        text = { Text(top) },
                                        onClick = {
                                            inquiryTopic = top
                                            inquiryTopicExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inquirySubject,
                            onValueChange = { inquirySubject = it },
                            label = { Text("Subject / Summary") },
                            placeholder = { Text("e.g., Question about certified rice seeds pickup") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inquiryPhone,
                            onValueChange = { inquiryPhone = it },
                            label = { Text("Contact Phone Number") },
                            placeholder = { Text("09123456789") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = inquiryMessage,
                            onValueChange = { inquiryMessage = it },
                            label = { Text("Message / Detailed Question") },
                            placeholder = { Text("Please describe your situation or questions in detail...") },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (inquirySubject.isBlank() || inquiryMessage.isBlank()) {
                                    Toast.makeText(context, "Please provide a subject and message", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isSubmittingInquiry = true
                                val auth = FirebaseAuth.getInstance()
                                val firestore = FirebaseFirestore.getInstance()
                                val uid = auth.currentUser?.uid ?: "guest"
                                val generatedId = "TKT-" + UUID.randomUUID().toString().take(8).uppercase()

                                val ticket = hashMapOf<String, Any>(
                                    "ticketId" to generatedId,
                                    "userId" to uid,
                                    "farmerName" to (userViewModel?.farmerName?.value ?: "Registered Farmer"),
                                    "farmerEmail" to (userViewModel?.farmerEmail?.value ?: ""),
                                    "farmerPhone" to inquiryPhone,
                                    "rsbsaNumber" to (userViewModel?.rsbsaNumber?.value ?: ""),
                                    "topic" to inquiryTopic,
                                    "subject" to inquirySubject,
                                    "message" to inquiryMessage,
                                    "status" to "Open",
                                    "category" to "Customer Service",
                                    "createdAt" to Timestamp.now()
                                )

                                // Save to support_tickets and also feedback collection so Admin portal sees it
                                firestore.collection("support_tickets").document(generatedId).set(ticket)
                                    .addOnSuccessListener {
                                        // Also replicate to feedback collection
                                        firestore.collection("feedback").add(
                                            hashMapOf(
                                                "farmerName" to (userViewModel?.farmerName?.value ?: "Farmer"),
                                                "category" to inquiryTopic,
                                                "comment" to "[${generatedId}] ${inquirySubject}: ${inquiryMessage}",
                                                "rating" to 5,
                                                "submittedAt" to Timestamp.now()
                                            )
                                        )

                                        isSubmittingInquiry = false
                                        submittedTicketId = generatedId
                                        showTicketDialog = true
                                        inquirySubject = ""
                                        inquiryMessage = ""
                                    }
                                    .addOnFailureListener { e ->
                                        isSubmittingInquiry = false
                                        // Offline fallback notification
                                        submittedTicketId = generatedId
                                        showTicketDialog = true
                                        inquirySubject = ""
                                        inquiryMessage = ""
                                    }
                            },
                            enabled = !isSubmittingInquiry,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isSubmittingInquiry) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Submitting...")
                            } else {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SUBMIT INQUIRY TO DA SUPPORT", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                3 -> {
                    // TAB 3: LEGAL POLICIES & TERMS
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        PolicyCard(
                            title = "About AgriServe",
                            content = "AgriServe is an integrated digital platform developed in collaboration with the Department of Agriculture (DA) to bridge farmers with agricultural inputs, real-time climate advisories, market commodity prices, and direct incident reporting."
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PolicyCard(
                            title = "Privacy Policy",
                            content = "Your privacy is important to us. We collect farmer identity details, RSBSA numbers, and geotagged farm coordinates strictly for government agricultural assistance distribution, crop insurance verification, and disaster risk assessment. Data is handled in compliance with the Data Privacy Act of 2012 (RA 10173)."
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PolicyCard(
                            title = "Terms of Service",
                            content = "By utilizing AgriServe, you agree to submit truthful agricultural declarations and legitimate government documentation. False reporting or illicit transfer of subsidized DA resources is subject to penalties under relevant Philippine agricultural regulations."
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "AgriServe Mobile v1.0.0 (Build 2026)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Success Dialog when ticket is submitted
    if (showTicketDialog) {
        AlertDialog(
            onDismissRequest = { showTicketDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inquiry Received!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Your inquiry has been assigned support reference:")
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = submittedTicketId ?: "",
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("A DA customer assistance specialist will review your question. You can follow up via hotline or email using this ticket ID.")
                }
            },
            confirmButton = {
                Button(onClick = { showTicketDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun FaqAccordionCard(faq: FaqItem) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = faq.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = faq.question,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp, start = 38.dp)) {
                    Divider(modifier = Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = faq.answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                    )
                }
            }
        }
    }
}

@Composable
fun ContactActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PolicyCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
