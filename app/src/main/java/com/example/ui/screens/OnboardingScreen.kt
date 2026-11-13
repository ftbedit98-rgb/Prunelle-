package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.PrivacyAudience

private data class CountryDial(val name: String, val code: String, val flag: String)

private val countryDials = listOf(
    CountryDial("France", "+33", "🇫🇷"),
    CountryDial("Belgique", "+32", "🇧🇪"),
    CountryDial("Suisse", "+41", "🇨🇭"),
    CountryDial("Canada", "+1", "🇨🇦"),
    CountryDial("Maroc", "+212", "🇲🇦"),
    CountryDial("Côte d'Ivoire", "+225", "🇨🇮"),
    CountryDial("Sénégal", "+221", "🇸🇳"),
    CountryDial("RDC", "+243", "🇨🇩"),
    CountryDial("Royaume-Uni", "+44", "🇬🇧")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    initialName: String = "",
    initialAvatarUri: String? = null,
    onCompleteSetup: (
        displayName: String,
        username: String,
        bio: String,
        avatarUri: String?,
        countryCode: String,
        phoneNumber: String,
        isPhoneVerified: Boolean,
        birthDay: Int?,
        birthMonth: Int?,
        birthYear: Int?,
        birthdayPrivacy: PrivacyAudience
    ) -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0 = Welcome, 1 = Phone, 2 = Profile
    var selectedCountry by remember { mutableStateOf(countryDials.first()) }
    var showCountryMenu by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    var displayName by remember(initialName) { mutableStateOf(initialName) }
    var username by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUri by remember(initialAvatarUri) { mutableStateOf(initialAvatarUri) }
    var birthDayText by remember { mutableStateOf("") }
    var birthMonthText by remember { mutableStateOf("") }
    var birthYearText by remember { mutableStateOf("") }
    var birthdayPrivacy by remember { mutableStateOf(PrivacyAudience.CONTACTS) }
    var profileError by remember { mutableStateOf<String?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) avatarUri = uri.toString()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_prunelle_hero_1794552305674),
                    contentDescription = "Prunelle",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.42f))
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Prunelle",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Messagerie privée, chaînes & création",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (step) {
                0 -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Une messagerie élégante, rapide et sécurisée",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Conversations chiffrées localement, mode hors ligne avec synchronisation automatique, chaînes, statuts 24h et fonds d'écran animés.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { step = 1 },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("start_onboarding_button")
                            ) {
                                Text("Créer un compte ou se connecter")
                            }
                            TextButton(
                                onClick = { showRecoveryDialog = true },
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .testTag("account_recovery_button")
                            ) {
                                Text("Récupérer un compte existant")
                            }
                        }
                    }
                }

                1 -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Vérification du numéro",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Votre numéro reste privé par défaut et n'est jamais affiché publiquement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Box {
                                OutlinedCard(
                                    onClick = { showCountryMenu = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("country_selector")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${selectedCountry.flag} ${selectedCountry.name} (${selectedCountry.code})")
                                        Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Choisir le pays")
                                    }
                                }
                                DropdownMenu(
                                    expanded = showCountryMenu,
                                    onDismissRequest = { showCountryMenu = false }
                                ) {
                                    countryDials.forEach { country ->
                                        DropdownMenuItem(
                                            text = { Text("${country.flag} ${country.name} (${country.code})") },
                                            onClick = {
                                                selectedCountry = country
                                                showCountryMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = {
                                    phoneInput = it.filter { ch -> ch.isDigit() || ch == ' ' }
                                    phoneError = null
                                },
                                label = { Text("Numéro de téléphone") },
                                prefix = { Text("${selectedCountry.code} ") },
                                placeholder = { Text("6 12 34 56 78") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                isError = phoneError != null,
                                supportingText = phoneError?.let { { Text(it) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("phone_input")
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = "Aucun faux code OTP n'est généré localement : l'envoi réel de SMS nécessite l'activation d'un fournisseur OTP backend (Firebase Auth ou API SMS dans Paramètres). Vous pouvez toutefois configurer votre profil local sécurisé dès maintenant.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { step = 0 },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retour")
                                }
                                Button(
                                    onClick = {
                                        val digits = phoneInput.filter { it.isDigit() }
                                        if (digits.length in 7..15) {
                                            step = 2
                                        } else {
                                            phoneError = "Entrez un numéro valide (7 à 15 chiffres)."
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .testTag("validate_phone_button")
                                ) {
                                    Text("Continuer")
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Créer votre profil Prunelle",
                                style = MaterialTheme.typography.titleLarge
                            )

                            Box(
                                modifier = Modifier
                                    .size(92.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .testTag("pick_profile_avatar"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (avatarUri != null) {
                                    AsyncImage(
                                        model = avatarUri,
                                        contentDescription = "Photo de profil",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Rounded.AddAPhoto,
                                            contentDescription = "Ajouter une photo",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "Photo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = displayName,
                                onValueChange = {
                                    displayName = it
                                    profileError = null
                                },
                                label = { Text("Nom affiché *") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_name_input")
                            )

                            OutlinedTextField(
                                value = username,
                                onValueChange = {
                                    username = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' || ch == '.' }
                                    profileError = null
                                },
                                label = { Text("Nom d'utilisateur unique *") },
                                prefix = { Text("@") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_username_input")
                            )

                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("Biographie (facultatif)") },
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_bio_input")
                            )

                            Text(
                                text = "Date d'anniversaire (facultatif)",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = birthDayText,
                                    onValueChange = { birthDayText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Jour") },
                                    placeholder = { Text("15") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("onboarding_birth_day")
                                )
                                OutlinedTextField(
                                    value = birthMonthText,
                                    onValueChange = { birthMonthText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("Mois") },
                                    placeholder = { Text("05") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("onboarding_birth_month")
                                )
                                OutlinedTextField(
                                    value = birthYearText,
                                    onValueChange = { birthYearText = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Année") },
                                    placeholder = { Text("Privée") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1.2f).testTag("onboarding_birth_year")
                                )
                            }
                            Text(
                                text = "Votre année de naissance reste strictement privée et n'est jamais affichée publiquement.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PrivacyAudience.entries.forEach { aud ->
                                    FilterChip(
                                        selected = birthdayPrivacy == aud,
                                        onClick = { birthdayPrivacy = aud },
                                        label = { Text(aud.labelFr) }
                                    )
                                }
                            }

                            profileError?.let {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = {
                                    val bDay = birthDayText.toIntOrNull()
                                    val bMonth = birthMonthText.toIntOrNull()
                                    val bYear = birthYearText.toIntOrNull()
                                    when {
                                        displayName.trim().length < 2 ->
                                            profileError = "Veuillez saisir votre nom (au moins 2 caractères)."
                                        username.trim().length < 3 ->
                                            profileError = "Le nom d'utilisateur doit contenir au moins 3 caractères."
                                        (bDay != null && bDay !in 1..31) || (bMonth != null && bMonth !in 1..12) ->
                                            profileError = "Date d'anniversaire invalide (jour 1–31, mois 1–12)."
                                        (bDay != null) != (bMonth != null) ->
                                            profileError = "Renseignez à la fois le jour et le mois d'anniversaire."
                                        else -> onCompleteSetup(
                                            displayName,
                                            username,
                                            bio,
                                            avatarUri,
                                            selectedCountry.code,
                                            phoneInput.filter { it.isDigit() },
                                            false,
                                            bDay,
                                            bMonth,
                                            bYear?.takeIf { it in 1900..2025 },
                                            birthdayPrivacy
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("finish_onboarding_button")
                            ) {
                                Text("Accéder à Prunelle")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Récupération de compte") },
            text = {
                Text(
                    "La récupération cloud à distance nécessite la connexion au backend Prunelle ou l'activation de Firebase dans AI Studio. Si vous utilisez cet appareil en mode local, vos données conservées sur le téléphone restent accessibles après connexion."
                )
            },
            confirmButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Compris")
                }
            }
        )
    }
}
