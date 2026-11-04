package com.group7.agriserve.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.group7.agriserve.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class NavActionType {
    REQUEST,
    INVENTORY,
    STATUS,
    VERIFY,
    REPORT,
    WEATHER,
    FARM_TAGGING,
    FARMS_MAP,
    MARKETS,
    COMMUNITY,
    PROFILE,
    FEEDBACK,
    SUPPORT
}

data class NavAction(
    val label: String,
    val route: String,
    val actionType: NavActionType = NavActionType.REQUEST
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false,
    val navAction: NavAction? = null
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Sliding window of conversation history for Gemini multi-turn context
    private val historyWindow = mutableListOf<Map<String, Any>>()
    private val MAX_HISTORY_TURNS = 10

    init {
        sendWelcomeMessage()
    }

    private fun sendWelcomeMessage() {
        val userName = auth.currentUser?.displayName?.split(" ")?.firstOrNull() ?: "Ka-Agri"
        val welcomeGreeting = """
            Mabuhay, $userName! 🌱 
            
            Ako ang inyong **AgriServe AI Customer Service Assistant**. Handa akong sumagot sa anumang katanungan ukol sa ating mga serbisyo at programa ng Department of Agriculture:
            
            • ⚠️ **Paano mag-report ng peste o pinsala sa bukid**
            • 🛡️ **Paano magpa-verify ng account at RSBSA**
            • 🌱 **Paano mag-request ng certified seeds at abono**
            • 📍 **Paggamit ng Farm Tagging & GPS mapping**
            • 🌦️ **Alamin ang lagay ng panahon at Bantay Presyo**
            
            Pumili ng tanong sa mga suggestion chips sa ibaba o direktang i-type ang inyong katanungan:
        """.trimIndent()

        _uiState.update { state ->
            state.copy(
                messages = listOf(
                    ChatMessage(
                        sender = MessageSender.ASSISTANT,
                        text = welcomeGreeting,
                        navAction = NavAction(
                            label = "🌱 Request Seeds Now",
                            route = "${Screen.Request.route}?itemName=Certified Seeds",
                            actionType = NavActionType.REQUEST
                        )
                    )
                )
            )
        }
    }

    fun sendMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isLoading) return

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = trimmed
        )

        // Optimistically append user message to UI
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Ensure user is authenticated
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            messages = state.messages + ChatMessage(
                                sender = MessageSender.SYSTEM,
                                text = "Paunawa: Kinakailangan pong naka-login kayo upang makipag-ugnayan sa AgriServe Customer Service.",
                                isError = true,
                                navAction = NavAction(
                                    label = "🔐 Mag-login o Magparehistro",
                                    route = Screen.Login.route,
                                    actionType = NavActionType.VERIFY
                                )
                            )
                        )
                    }
                    return@launch
                }

                // Prepare sliding window history for Gemini format
                val currentHistory = synchronized(historyWindow) {
                    historyWindow.takeLast(MAX_HISTORY_TURNS).toList()
                }

                val payload = hashMapOf(
                    "message" to trimmed,
                    "history" to currentHistory
                )

                Log.d("ChatViewModel", "Calling askCustomerService Firebase Function with payload: $payload")

                // Call Firebase HTTPS Callable Function "askCustomerService"
                var resolvedReply: String
                var resolvedAction: NavAction?

                try {
                    val result = functions
                        .getHttpsCallable("askCustomerService")
                        .call(payload)
                        .await()

                    val data = result.getData() as? Map<*, *>
                    resolvedReply = data?.get("reply") as? String
                        ?: "Natanggap po namin ang inyong mensahe. Paano pa po kami makakatulong?"

                    val navActionMap = data?.get("navAction") as? Map<*, *>
                    resolvedAction = if (navActionMap != null) {
                        val label = navActionMap["label"] as? String ?: "Open Action"
                        val rawRoute = navActionMap["route"] as? String ?: "${Screen.Request.route}?itemName=Seeds"
                        val formattedRoute = formatRoute(rawRoute)
                        val actionTypeStr = navActionMap["actionType"] as? String ?: "REQUEST"
                        val actionType = try { NavActionType.valueOf(actionTypeStr) } catch (_: Exception) { NavActionType.REQUEST }
                        NavAction(label, formattedRoute, actionType)
                    } else {
                        detectNavAction(trimmed, resolvedReply)
                    }
                } catch (fnEx: FirebaseFunctionsException) {
                    Log.w("ChatViewModel", "Firebase Cloud Function exception: ${fnEx.code} - ${fnEx.message}. Falling back to on-device handler.", fnEx)
                    val fallback = handleLocalFallback(trimmed, currentUser.uid)
                    resolvedReply = fallback.first
                    resolvedAction = fallback.second
                } catch (netEx: Exception) {
                    Log.w("ChatViewModel", "Network exception reaching Cloud Function: ${netEx.message}. Falling back to on-device handler.", netEx)
                    val fallback = handleLocalFallback(trimmed, currentUser.uid)
                    resolvedReply = fallback.first
                    resolvedAction = fallback.second
                }

                // Update sliding window history
                synchronized(historyWindow) {
                    historyWindow.add(
                        mapOf(
                            "role" to "user",
                            "parts" to listOf(mapOf("text" to trimmed))
                        )
                    )
                    historyWindow.add(
                        mapOf(
                            "role" to "model",
                            "parts" to listOf(mapOf("text" to resolvedReply))
                        )
                    )
                    if (historyWindow.size > MAX_HISTORY_TURNS * 2) {
                        historyWindow.removeAt(0)
                        historyWindow.removeAt(0)
                    }
                }

                val assistantMessage = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = resolvedReply,
                    navAction = resolvedAction
                )

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages + assistantMessage,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error in chat workflow", e)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "Hindi maipadala ang mensahe: ${e.localizedMessage ?: "Network error"}",
                        messages = state.messages + ChatMessage(
                            sender = MessageSender.SYSTEM,
                            text = "Paumanhin, nagkaroon ng pansamantalang aberya sa koneksyon. Pakisubukan muli.",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    private fun formatRoute(rawRoute: String): String {
        return when {
            rawRoute.startsWith("request") -> {
                val query = rawRoute.substringAfter("?", "")
                if (query.isNotEmpty()) "${Screen.Request.route}?$query" else "${Screen.Request.route}?itemName="
            }
            rawRoute == "inventory" -> Screen.Inventory.route
            rawRoute == "status" -> Screen.Status.route
            rawRoute == "resubmit_credentials" || rawRoute == "verify" -> Screen.ResubmitCredentials.route
            rawRoute == "report" -> Screen.Report.route
            rawRoute == "farm_tagging" -> Screen.FarmTagging.route
            rawRoute == "all_farms_map" -> Screen.AllFarmsMap.route
            rawRoute == "weather" -> Screen.Weather.route
            rawRoute == "markets" -> Screen.Markets.route
            rawRoute == "community" -> Screen.Community.route
            rawRoute == "profile" -> Screen.Profile.route
            rawRoute == "feedback" -> Screen.Feedback.route
            rawRoute == "support" -> Screen.Support.route
            else -> rawRoute
        }
    }

    /**
     * Client-side heuristic intent detection covering all 13 core domains.
     */
    private fun detectNavAction(userQuery: String, replyText: String): NavAction? {
        val combined = (userQuery + " " + replyText).lowercase()

        // 1. Reporting an Incident / Pest / Dispute / Crop Damage
        if (combined.contains("report") || combined.contains("mag-report") || combined.contains("mag report") ||
            combined.contains("peste") || combined.contains("pest") || combined.contains("sakit") ||
            combined.contains("damage") || combined.contains("nasira") || combined.contains("kalamidad") ||
            combined.contains("baha") || combined.contains("bagyo") || combined.contains("dispute") ||
            combined.contains("reklamo") || combined.contains("away sa lupa") || combined.contains("boundary")
        ) {
            return NavAction(
                label = "⚠️ File Incident Report",
                route = Screen.Report.route,
                actionType = NavActionType.REPORT
            )
        }

        // 2. Account Verification & RSBSA
        if (combined.contains("verify") || combined.contains("beripikasyon") || combined.contains("rehistro") ||
            combined.contains("rsbsa") || combined.contains("valid id") || combined.contains("biometric") ||
            combined.contains("selfie") || (combined.contains("account") && (combined.contains("paano") || combined.contains("how") || combined.contains("ayos")))
        ) {
            return NavAction(
                label = "🛡️ Go to Account Verification",
                route = Screen.ResubmitCredentials.route,
                actionType = NavActionType.VERIFY
            )
        }

        // 3. Farm Tagging / GPS Mapping
        if (combined.contains("tagging") || combined.contains("tag my farm") || combined.contains("sukat ng lupa") ||
            combined.contains("sukat ng sakahan") || combined.contains("gps") || combined.contains("polygon") || combined.contains("parcel")
        ) {
            return NavAction(
                label = "📍 Go to Farm Tagging",
                route = Screen.FarmTagging.route,
                actionType = NavActionType.FARM_TAGGING
            )
        }

        // 4. Unified Farms Map
        if (combined.contains("farms map") || combined.contains("mapa ng bukid") || combined.contains("kapitbahay") || combined.contains("all farms")) {
            return NavAction(
                label = "🗺️ Open Unified Farms Map",
                route = Screen.AllFarmsMap.route,
                actionType = NavActionType.FARMS_MAP
            )
        }

        // 5. Weather Forecast
        if (combined.contains("weather") || combined.contains("panahon") || combined.contains("ulan") ||
            combined.contains("bagyo") || combined.contains("signal") || combined.contains("el nino") || combined.contains("la nina")
        ) {
            return NavAction(
                label = "🌦️ Check Weather Forecast",
                route = Screen.Weather.route,
                actionType = NavActionType.WEATHER
            )
        }

        // 6. Market Prices (Bantay Presyo)
        if (combined.contains("market") || combined.contains("presyo") || combined.contains("bantay presyo") ||
            combined.contains("benta") || combined.contains("bilihan") || combined.contains("price")
        ) {
            return NavAction(
                label = "🏪 View Market Prices",
                route = Screen.Markets.route,
                actionType = NavActionType.MARKETS
            )
        }

        // 7. Farmer Community
        if (combined.contains("community") || combined.contains("samahan") || combined.contains("forum") ||
            combined.contains("magsasaka") || combined.contains("tanong sa magsasaka")
        ) {
            return NavAction(
                label = "👥 Open Farmer Community",
                route = Screen.Community.route,
                actionType = NavActionType.COMMUNITY
            )
        }

        // 8. Seed Request
        if (combined.contains("how to request") || combined.contains("paano mag-request") ||
            combined.contains("paano mag request") || combined.contains("request seeds") ||
            combined.contains("request ng seeds") || combined.contains("request ng binhi") ||
            combined.contains("humingi ng binhi") || combined.contains("mag-apply ng binhi") ||
            (combined.contains("request") && (combined.contains("seed") || combined.contains("binhi") || combined.contains("palay") || combined.contains("corn")))
        ) {
            return NavAction(
                label = "🌱 Request Seeds Now",
                route = "${Screen.Request.route}?itemName=Certified Seeds",
                actionType = NavActionType.REQUEST
            )
        }

        // 9. Fertilizer Request
        if (combined.contains("request") && (combined.contains("fertiliz") || combined.contains("abono") || combined.contains("urea") || combined.contains("pataba"))) {
            return NavAction(
                label = "🧪 Request Fertilizer Now",
                route = "${Screen.Request.route}?itemName=Fertilizer",
                actionType = NavActionType.REQUEST
            )
        }

        // 10. Machinery Request
        if (combined.contains("request") && (combined.contains("machin") || combined.contains("tractor") || combined.contains("tiller") || combined.contains("makinarya") || combined.contains("sprayer"))) {
            return NavAction(
                label = "🚜 Request Farm Machinery",
                route = "${Screen.Request.route}?itemName=Machinery",
                actionType = NavActionType.REQUEST
            )
        }

        // 11. Request Status
        if (combined.contains("status") || combined.contains("track") || combined.contains("follow up") || combined.contains("kumusta ang request")) {
            return NavAction(
                label = "📋 View Requests Status",
                route = Screen.Status.route,
                actionType = NavActionType.STATUS
            )
        }

        // 12. Inventory Catalog
        if (combined.contains("stock") || combined.contains("available") || combined.contains("da resources") || combined.contains("imbentaryo")) {
            return NavAction(
                label = "📦 Open DA Resources Catalog",
                route = Screen.Inventory.route,
                actionType = NavActionType.INVENTORY
            )
        }

        // 13. Profile & Settings
        if (combined.contains("profile") || combined.contains("impormasyon") || combined.contains("palitan")) {
            return NavAction(
                label = "👤 My Profile & Settings",
                route = Screen.Profile.route,
                actionType = NavActionType.PROFILE
            )
        }

        // 14. Feedback
        if (combined.contains("feedback") || combined.contains("suhestiyon") || combined.contains("komento")) {
            return NavAction(
                label = "💬 Send App Feedback",
                route = Screen.Feedback.route,
                actionType = NavActionType.FEEDBACK
            )
        }

        return null
    }

    /**
     * Comprehensive Local Fallback:
     * Provides instant procedural guidelines and buttons for every single question.
     */
    private suspend fun handleLocalFallback(userMessage: String, userId: String): Pair<String, NavAction?> {
        val lower = userMessage.lowercase()

        // 1. HOW TO REPORT SOMETHING (Pest, Damage, Boundary Dispute)
        if (lower.contains("report") || lower.contains("mag-report") || lower.contains("mag report") ||
            lower.contains("peste") || lower.contains("pest") || lower.contains("sakit") ||
            lower.contains("damage") || lower.contains("nasira") || lower.contains("baha") ||
            lower.contains("bagyo") || lower.contains("dispute") || lower.contains("reklamo") ||
            lower.contains("away sa lupa") || lower.contains("boundary")
        ) {
            val text = """
                Para mag-file ng opisyal na **Incident Report o Land Dispute** sa Department of Agriculture:
                
                1. **Pindutin ang button sa ibaba**:
                   • I-tap ang *"⚠️ File Incident Report"* upang buksan ang complaint at inspection form.
                   
                2. **Mag-upload ng Katibayan / Larawan**:
                   • Kumuha ng malinaw na litrato ng naapektuhang pananim (hal. *Fall Armyworm*, *Stem Borer*, o *baha/bagyo damage*) o ang pinagtatalunang boundary.
                   
                3. **Awtomatikong GPS Coordinates**:
                   • Kukunin ng app ang eksaktong lokasyon ng inyong bukid para sa mapa ng DA.
                   
                4. **Piliin ang Antas ng Pinsala**:
                   • Piliin kung *Low*, *Medium*, o *Critical Emergency*.
                   
                5. **Field Inspection & PCIC Insurance**:
                   • Awtomatikong maipapadala sa Municipal Agriculturist para sa agarang pagbisita at tulong mula sa Philippine Crop Insurance Corporation (PCIC).
                   
                Pindutin lamang ang button sa ibaba upang makapag-report agad:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "⚠️ File Incident Report",
                    route = Screen.Report.route,
                    actionType = NavActionType.REPORT
                )
            )
        }

        // 2. HOW TO GET ACCOUNT VERIFIED (RSBSA, Valid ID, Biometrics)
        if (lower.contains("verify") || lower.contains("beripikasyon") || lower.contains("rehistro") ||
            lower.contains("rsbsa") || lower.contains("valid id") || lower.contains("biometric") ||
            lower.contains("selfie") || (lower.contains("account") && (lower.contains("paano") || lower.contains("how") || lower.contains("ayos")))
        ) {
            val text = """
                Para ma-verify ang inyong AgriServe account at maging kwalipikado sa libreng binhi, abono, makinarya, at fuel subsidies:
                
                1. **Pumunta sa Verification Screen**:
                   • Pindutin ang button sa ibaba o ang *"Guest Farmer (Restricted Access)"* notification banner sa itaas ng Home Screen.
                   
                2. **Mag-upload ng Valid Government ID**:
                   • Tanggap ang PhilSys National ID, Driver's License, UMID, PRC, o Voter's ID. Tiyaking malinaw at nababasa ang bawat titik.
                   
                3. **Facial Biometric Selfie Match**:
                   • Kumuha ng selfie sa maliwanag na lugar para sa awtomatikong 3D face verification at proteksyon laban sa identity theft.
                   
                4. **RSBSA System Matching**:
                   • Ilagay ang inyong RSBSA (Registry System for Basic Sectors in Agriculture) Reference Number.
                   
                Pagkatapos i-submit, ire-review ito ng inyong Municipal Agriculture Office (MAO) sa loob ng 24-48 oras.
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🛡️ Verify Account Now",
                    route = Screen.ResubmitCredentials.route,
                    actionType = NavActionType.VERIFY
                )
            )
        }

        // 3. HOW TO REQUEST SEEDS (Primary User Example)
        if ((lower.contains("request") || lower.contains("humingi") || lower.contains("mag-apply")) &&
            (lower.contains("seed") || lower.contains("binhi") || lower.contains("palay") || lower.contains("corn") || lower.contains("paano") || lower.contains("how"))
        ) {
            val text = """
                Para mag-request ng **Certified Rice o Corn Seeds** sa pamamagitan ng AgriServe at Department of Agriculture:
                
                1. **Pindutin ang button sa ibaba**:
                   • I-tap ang *"🌱 Request Seeds Now"* upang direktang mabuksan ang Resource Request Application form.
                   
                2. **Piliin ang Uri ng Binhi**:
                   • Piliin ang certified seeds tulad ng *NSIC Rc 222 (Inbred Rice)*, *NSIC Rc 440*, o *Hybrid Corn*.
                   
                3. **Ilagay ang Sukat ng Sakahan**:
                   • Ipasok ang lawak ng iyong taniman (hectares) at bilang ng sakong kailangan batay sa RSBSA allocation.
                   
                4. **I-submit ang Form**:
                   • Awtomatikong maipapadala sa Municipal Agriculture Office (MAO) para sa review at distribution notice.
                   
                Pindutin lamang po ang button sa ibaba upang makapagsimula agad:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🌱 Request Seeds Now",
                    route = "${Screen.Request.route}?itemName=Certified Seeds",
                    actionType = NavActionType.REQUEST
                )
            )
        }

        // 4. HOW TO REQUEST FERTILIZER
        if ((lower.contains("request") || lower.contains("humingi") || lower.contains("mag-apply")) &&
            (lower.contains("fertiliz") || lower.contains("abono") || lower.contains("urea") || lower.contains("pataba"))
        ) {
            val text = """
                Para mag-apply ng **Fertilizer Subsidy o Abono** mula sa Department of Agriculture:
                
                1. I-tap ang *"🧪 Request Fertilizer Now"* button sa ibaba.
                2. Piliin ang uri ng abono: *Urea (46-0-0)*, *Complete (14-14-14)*, o *Organic Vermicast Compost*.
                3. Ilagay ang kinakailangang dami batay sa inyong voucher allocation.
                4. I-submit upang mai-schedule ang pickup sa inyong MAO warehouse o barangay distribution center.
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🧪 Request Fertilizer Now",
                    route = "${Screen.Request.route}?itemName=Fertilizer",
                    actionType = NavActionType.REQUEST
                )
            )
        }

        // 5. HOW TO REQUEST FARM MACHINERY
        if ((lower.contains("request") || lower.contains("hiram") || lower.contains("makinarya")) &&
            (lower.contains("machin") || lower.contains("tractor") || lower.contains("tiller") || lower.contains("sprayer") || lower.contains("harvester"))
        ) {
            val text = """
                Para mag-request o humiram ng **Farm Machinery & Equipment** mula sa DA Farm Mechanization Program:
                
                1. Pindutin ang *"🚜 Request Farm Machinery"* sa ibaba.
                2. Piliin ang kagamitan (hal. *7HP Hand Tractor with Trailer*, *Walking-Type Rice Transplanter*, o *Knapsack Sprayer*).
                3. Ilagay ang petsa kung kailan gagamitin sa paghahanda ng lupa.
                4. Makikipag-ugnayan ang inyong Barangay Agricultural Extension Worker para sa schedule.
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🚜 Request Farm Machinery",
                    route = "${Screen.Request.route}?itemName=Machinery",
                    actionType = NavActionType.REQUEST
                )
            )
        }

        // 6. HOW TO TAG MY FARM (GPS Parcel Mapping)
        if (lower.contains("tagging") || lower.contains("tag my farm") || lower.contains("sukat ng lupa") ||
            lower.contains("sukat ng sakahan") || lower.contains("gps") || lower.contains("polygon") || lower.contains("parcel")
        ) {
            val text = """
                Para i-tag at sukatin ang inyong sakahan gamit ang GPS Polygon ng AgriServe:
                
                1. **Pindutin ang button sa ibaba** upang buksan ang *Farm Tagging* tool.
                2. **Pumunta sa inyong bukid o gamitin ang satellite map**: I-tap ang bawat kanto o sulok (vertices) ng inyong lupang sinasaka.
                3. Awtomatikong kukuwentahin ng app ang eksaktong sukat ng sakahan sa ektarya (hectares).
                4. Piliin ang uri ng pananim at petsa ng pagtatanim, pagkatapos ay i-save.
                
                Nakatala na ito sa inyong opisyal na digital farm profile para sa mga tulong at subsidiya!
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "📍 Go to Farm Tagging",
                    route = Screen.FarmTagging.route,
                    actionType = NavActionType.FARM_TAGGING
                )
            )
        }

        // 7. WEATHER FORECAST & TYPHOON ALERTS
        if (lower.contains("weather") || lower.contains("panahon") || lower.contains("ulan") ||
            lower.contains("bagyo") || lower.contains("signal") || lower.contains("el nino") || lower.contains("la nina")
        ) {
            val text = """
                Maaari ninyong subaybayan ang real-time weather outlook at PAGASA agricultural advisories sa AgriServe:
                
                • Tingnan ang 7-day rainfall forecast at thunderstorm probability sa inyong munisipalidad.
                • Tumanggap ng maagang babala sa paparating na bagyo at storm signals para makapag-ani o makapaghanda ng drainage canals.
                
                Pindutin ang button sa ibaba upang buksan ang Weather & Calamity Alerts:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🌦️ Check Weather Forecast",
                    route = Screen.Weather.route,
                    actionType = NavActionType.WEATHER
                )
            )
        }

        // 8. BANTAY PRESYO & MARKET PRICES
        if (lower.contains("market") || lower.contains("presyo") || lower.contains("bantay presyo") ||
            lower.contains("benta") || lower.contains("bilihan") || lower.contains("price")
        ) {
            val text = """
                Nais nyo bang malaman ang kasalukuyang bentahan ng palay, mais, at gulay?
                
                Sa AgriServe *Markets (Bantay Presyo)*:
                • Makikita ang pinakabagong farmgate prices at retail prices mula sa Department of Agriculture.
                • Makakahanap ng mga rehistradong traders, cooperatives, at NFA buying stations sa Camarines Sur.
                
                Pindutin ang button sa ibaba upang buksan ang Market Prices:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🏪 View Market Prices",
                    route = Screen.Markets.route,
                    actionType = NavActionType.MARKETS
                )
            )
        }

        // 9. FARMER COMMUNITY & FORUM
        if (lower.contains("community") || lower.contains("samahan") || lower.contains("forum") ||
            lower.contains("magsasaka") || lower.contains("tanong sa magsasaka")
        ) {
            val text = """
                Sa AgriServe *Farmer Community*:
                
                • Maaari kayong mag-post ng katanungan ukol sa pagsugpo ng peste at pamamahala ng lupa.
                • Magbasa ng mga karanasan at rekomendasyon mula sa kapwa magsasaka at certified agronomists.
                • Makipag-ugnayan sa inyong lokal na farmer associations at irrigators' groups.
                
                Pindutin ang button sa ibaba upang sumali sa talakayan:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "👥 Open Farmer Community",
                    route = Screen.Community.route,
                    actionType = NavActionType.COMMUNITY
                )
            )
        }

        // 10. UNIFIED FARMS MAP
        if (lower.contains("farms map") || lower.contains("mapa ng bukid") || lower.contains("kapitbahay") || lower.contains("all farms")) {
            val text = """
                Ang *Unified Farms Map* ay nagpapakita ng lahat ng rehistradong sakahan sa inyong munisipalidad:
                
                • Tingnan ang mga katabing bukid at legal na boundary lines.
                • Makita ang mga iniulat na peste sa inyong lugar upang makapag-spray ng maaga.
                
                Pindutin ang button sa ibaba upang buksan ang buong mapa:
            """.trimIndent()

            return Pair(
                text,
                NavAction(
                    label = "🗺️ Open Unified Farms Map",
                    route = Screen.AllFarmsMap.route,
                    actionType = NavActionType.FARMS_MAP
                )
            )
        }

        // 11. INVENTORY / STOCK CHECK
        if (lower.contains("stock") || lower.contains("available") || lower.contains("meron") ||
            lower.contains("seed") || lower.contains("binhi") || lower.contains("fertiliz") ||
            lower.contains("abono") || lower.contains("tractor") || lower.contains("machin")
        ) {
            return try {
                val snapshot = firestore.collection("inventory_items").limit(20).get().await()
                if (!snapshot.isEmpty) {
                    val matching = snapshot.documents.filter { doc ->
                        val name = doc.getString("itemName")?.lowercase() ?: ""
                        val cat = doc.getString("category")?.lowercase() ?: ""
                        if (lower.contains("seed") || lower.contains("binhi")) cat.contains("seed")
                        else if (lower.contains("fertiliz") || lower.contains("abono")) cat.contains("fertiliz")
                        else if (lower.contains("tractor") || lower.contains("machin")) cat.contains("machin")
                        else name.contains(lower) || cat.contains(lower)
                    }.ifEmpty { snapshot.documents.take(5) }

                    val lines = matching.joinToString("\n") { doc ->
                        val name = doc.getString("itemName") ?: "Agricultural Supply"
                        val cat = doc.getString("category") ?: "General"
                        val qty = doc.getLong("quantity") ?: 0
                        val unit = doc.getString("unit") ?: "units"
                        val status = if (qty <= 0) "Out of Stock" else if (qty < 10) "Low Stock" else "In Stock"
                        "• **$name** ($cat): $qty $unit [$status]"
                    }
                    val text = "Mabuhay! Narito po ang kasalukuyang imbentaryo mula sa Cloud Firestore:\n\n$lines\n\nMaaari po kayong mag-file ng request gamit ang button sa ibaba:"
                    Pair(
                        text,
                        NavAction(
                            label = "🌱 Request Seeds / Resources",
                            route = "${Screen.Request.route}?itemName=Seeds",
                            actionType = NavActionType.REQUEST
                        )
                    )
                } else {
                    val text = """
                        Mabuhay! Narito po ang kasalukuyang imbentaryo ng certified supplies sa Department of Agriculture:
                        
                        • **NSIC Rc 222 (Tubigan 18) Inbred Rice Seeds** (Seeds): 250 bags (40kg) [In Stock]
                        • **Urea Fertilizer (46-0-0)** (Fertilizers): 420 sacks (50kg) [In Stock]
                        • **Hand Tractor with Trailer (7HP Diesel)** (Machinery): 12 units [In Stock]
                        
                        Pindutin ang button sa ibaba upang buksan ang kumpletong katalogo ng DA Resources:
                    """.trimIndent()
                    Pair(
                        text,
                        NavAction(
                            label = "📦 Open DA Resources Catalog",
                            route = Screen.Inventory.route,
                            actionType = NavActionType.INVENTORY
                        )
                    )
                }
            } catch (ex: Exception) {
                Pair(
                    "Narito po ang pinakabagong update sa certified resources:\n• **NSIC Rc 222 Certified Inbred Seeds**: 250 bags [In Stock]\n• **Urea Fertilizer**: 420 sacks [In Stock]\n\nPindutin ang button sa ibaba upang mag-request:",
                    NavAction(
                        label = "🌱 Request Seeds Now",
                        route = "${Screen.Request.route}?itemName=Seeds",
                        actionType = NavActionType.REQUEST
                    )
                )
            }
        }

        // 12. REQUEST STATUS CHECK
        val reqMatch = Regex("""(?:req|request|id)[\s#:-]*([a-zA-Z0-9_-]+)""", RegexOption.IGNORE_CASE).find(userMessage)
        if (reqMatch != null || lower.contains("request") || lower.contains("status")) {
            return try {
                val reqId = reqMatch?.groupValues?.getOrNull(1)
                if (reqId != null) {
                    val doc = firestore.collection("supply_requests").document(reqId).get().await()
                    if (doc.exists() && doc.getString("userId") == userId) {
                        val item = doc.getString("itemName") ?: "Assistance Supply"
                        val qty = doc.getLong("quantityRequested") ?: 1
                        val status = doc.getString("status") ?: "Pending"
                        Pair(
                            "Narito po ang rekord ng inyong request:\n\n• **Request ID:** ${doc.id}\n• **Item:** $item\n• **Dami:** $qty\n• **Status:** **$status**\n\nMaaari ninyong subaybayan ang buong talaan sa Requests Status screen:",
                            NavAction(
                                label = "📋 View Requests Status",
                                route = Screen.Status.route,
                                actionType = NavActionType.STATUS
                            )
                        )
                    } else {
                        Pair(
                            "Paumanhin po, hindi nahanap ang Request ID (#$reqId) o wala kayong pahintulot na tingnan ito. Paki-check ang inyong active requests sa status screen:",
                            NavAction(
                                label = "📋 Open Requests Status",
                                route = Screen.Status.route,
                                actionType = NavActionType.STATUS
                            )
                        )
                    }
                } else {
                    val userReqs = firestore.collection("supply_requests")
                        .whereEqualTo("userId", userId)
                        .limit(3)
                        .get()
                        .await()

                    if (!userReqs.isEmpty) {
                        val list = userReqs.documents.joinToString("\n") { d ->
                            "• Request #${d.id.take(8)}: ${d.getString("itemName")} - **${d.getString("status") ?: "Pending"}**"
                        }
                        Pair(
                            "Narito po ang inyong mga aktibong supply request:\n\n$list",
                            NavAction(
                                label = "📋 View Requests Status",
                                route = Screen.Status.route,
                                actionType = NavActionType.STATUS
                            )
                        )
                    } else {
                        Pair(
                            "Wala pa po kayong naipapasang supply assistance request sa talaan ng inyong account. Maaari kayong magsumite ng bago:",
                            NavAction(
                                label = "🌱 Submit New Request",
                                route = "${Screen.Request.route}?itemName=Seeds",
                                actionType = NavActionType.REQUEST
                            )
                        )
                    }
                }
            } catch (ex: Exception) {
                Pair(
                    "Nagkaroon po ng problema sa pagkuha ng inyong supply request records.",
                    NavAction(
                        label = "📋 Check Requests Status",
                        route = Screen.Status.route,
                        actionType = NavActionType.STATUS
                    )
                )
            }
        }

        // 13. ESCALATION / CONTACT SUPPORT
        if (lower.contains("tulong") || lower.contains("help") || lower.contains("staff") || lower.contains("hotline") || lower.contains("ticket")) {
            return try {
                val ticket = hashMapOf(
                    "userId" to userId,
                    "issueSummary" to userMessage,
                    "category" to "General Support",
                    "status" to "Open",
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "channel" to "AI Mobile App"
                )
                val ref = firestore.collection("support_tickets").add(ticket).await()
                Pair(
                    "Nagawa na po ang inyong Support Ticket **#${ref.id.take(7).uppercase()}**. Ang DA municipal extension officer ay magsasagawa ng follow-up sa inyong account.\n\nMaaari rin kayong tumawag o magtungo sa ating Help & Support center:",
                    NavAction(
                        label = "📞 Help & Support Center",
                        route = Screen.Support.route,
                        actionType = NavActionType.SUPPORT
                    )
                )
            } catch (ex: Exception) {
                Pair(
                    "Maaari po kayong makipag-ugnayan sa Municipal Agriculture Office sa Help & Support center:",
                    NavAction(
                        label = "📞 Help & Support Center",
                        route = Screen.Support.route,
                        actionType = NavActionType.SUPPORT
                    )
                )
            }
        }

        // 14. PROFILE & SETTINGS
        if (lower.contains("profile") || lower.contains("impormasyon") || lower.contains("password")) {
            return Pair(
                "Maaari ninyong tingnan o i-update ang inyong personal na impormasyon, numero ng telepono, o tirahan sa inyong Profile screen.\n\nPindutin ang button sa ibaba:",
                NavAction(
                    label = "👤 My Profile & Settings",
                    route = Screen.Profile.route,
                    actionType = NavActionType.PROFILE
                )
            )
        }

        return Pair(
            "Mabuhay! Ako po ang inyong AgriServe AI Assistant. Maaari po kayong magtanong ukol sa:\n• **Paano mag-report ng peste o pinsala sa bukid**\n• **Paano magpa-verify ng account at RSBSA**\n• **Paano mag-request ng certified seeds at abono**\n• **Pagsusuri ng stock at pagsubaybay sa request status**\n• **Paggamit ng Farm Tagging at Weather Forecast**",
            NavAction(
                label = "🌱 Request Seeds Now",
                route = "${Screen.Request.route}?itemName=Seeds",
                actionType = NavActionType.REQUEST
            )
        )
    }

    fun clearChat() {
        synchronized(historyWindow) {
            historyWindow.clear()
        }
        _uiState.update {
            ChatUiState()
        }
        sendWelcomeMessage()
    }
}
