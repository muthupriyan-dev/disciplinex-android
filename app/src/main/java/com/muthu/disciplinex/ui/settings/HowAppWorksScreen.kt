package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.Danger
import com.muthu.disciplinex.ui.theme.OrangeEnd
import com.muthu.disciplinex.ui.theme.Success
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.Warning as WarningColor

/**
 * "How DisciplineX Works": a read-only explanation of the app in five sections, written
 * as situations in plain language. It reuses the Settings building blocks
 * ([SettingsSection], [SettingsGroup], [SettingsRow]) so it looks like the rest of the app
 * in both themes and both languages, and it never changes any setting, alarm or lock.
 *
 * Every number below (5 minutes away, 10 minutes wait, 30 minutes unlock, 24 hours lock,
 * 2 hours before an alarm, 30 second password) is taken from ChallengeSession, AlarmEntry
 * and UninstallGuard. If you change one of those rules, change the text here too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowAppWorksScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        tr("How DisciplineX Works", "DisciplineX எப்படி செயல்படுகிறது?"),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = tr("Back", "பின்செல்"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                tr(
                    "Finish your challenge and your phone is yours again. Here is what happens in each situation.",
                    "சவாலை முடித்தால் உங்கள் போன் மீண்டும் உங்களுடையது. ஒவ்வொரு சூழலிலும் என்ன நடக்கும் என்பது இங்கே."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp)
            )

            sections().forEach { section ->
                SettingsSection(section.title) {
                    if (section.intro != null) {
                        Text(
                            section.intro,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 10.dp)
                        )
                    }
                    SettingsGroup {
                        section.items.forEachIndexed { index, item ->
                            if (index > 0) GroupDivider()
                            SettingsRow(
                                icon = item.icon,
                                title = item.title,
                                subtitle = item.body,
                                iconTint = item.tint
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private data class Situation(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val tint: Color = OrangeEnd
)

private data class GuideSection(
    val title: String,
    val items: List<Situation>,
    val intro: String? = null
)

private fun sections(): List<GuideSection> = listOf(
    // 1 ---------------------------------------------------------------------------------
    GuideSection(
        title = tr("When Your Alarm Rings", "அலாரம் ஒலிக்கும்போது"),
        items = listOf(
            Situation(
                Icons.Filled.Alarm,
                tr("Your challenge starts", "உங்கள் சவால் தொடங்கும்"),
                tr(
                    "As soon as your alarm rings, your challenge begins and the time limit you set starts counting down.",
                    "அலாரம் ஒலிக்கத் தொடங்கியதும் உங்கள் சவால் தொடங்கும். நீங்கள் அமைத்த நேர வரம்பும் குறையத் தொடங்கும்."
                )
            ),
            Situation(
                Icons.Filled.Lock,
                tr("Your phone gets locked", "போன் பூட்டப்படும்"),
                tr(
                    "Until you finish, only Phone, Camera and the apps you allowed will open. Phone Settings is blocked too.",
                    "சவாலை முடிக்கும் வரை போன், கேமரா, நீங்கள் அனுமதித்த ஆப்கள் மட்டுமே திறக்கும். போன் அமைப்புகளும் தடுக்கப்படும்."
                ),
                WarningColor
            ),
            Situation(
                Icons.Filled.VolumeOff,
                tr("Stopping the sound is not enough", "ஒலியை நிறுத்தினால் மட்டும் போதாது"),
                tr(
                    "Dismiss only stops the sound. The time limit keeps running and your phone stays locked.",
                    "நிறுத்து என்பதை தட்டினால் ஒலி மட்டுமே நிற்கும். நேர வரம்பு தொடர்ந்து ஓடும், போன் பூட்டியே இருக்கும்."
                )
            ),
            Situation(
                Icons.Filled.LockOpen,
                tr("Finish to unlock", "முடித்தால் போன் திறக்கும்"),
                tr(
                    "Complete your exercise within the time limit and your phone unlocks. That day is added to your streak.",
                    "நேர வரம்புக்குள் பயிற்சியை முடித்தால் போன் திறக்கும். அந்த நாள் உங்கள் தொடர்ச்சியான சாதனையில் சேரும்."
                ),
                Success
            )
        )
    ),
    // 2 ---------------------------------------------------------------------------------
    GuideSection(
        title = tr("Complete Your Challenge", "உங்கள் சவாலை முடிக்கவும்"),
        intro = tr(
            "Every alarm has one exercise and one target. Your progress is saved automatically.",
            "ஒவ்வொரு அலாரத்திலும் ஒரு பயிற்சியும் ஓர் இலக்கும் இருக்கும். உங்கள் முன்னேற்றம் தானாகவே பதிவாகும்."
        ),
        items = listOf(
            Situation(
                Icons.Filled.FitnessCenter,
                tr("Push-ups, Squats, Jumping Jacks", "புஷ்-அப்ஸ், ஸ்குவாட்ஸ், ஜம்பிங் ஜாக்ஸ்"),
                tr(
                    "The camera counts every rep on your phone. Keep your whole body in view, because only full reps count.",
                    "ஒவ்வொரு முறையையும் கேமரா உங்கள் போனிலேயே எண்ணும். உங்கள் முழு உடலும் கேமராவில் தெரிய வேண்டும், முழுமையான முறைகள் மட்டுமே கணக்கில் வரும்."
                )
            ),
            Situation(
                Icons.Filled.DirectionsWalk,
                tr("Walking", "நடைப்பயிற்சி"),
                tr(
                    "Walk the target distance with your phone. The distance is estimated from your phone's step counter, even when the screen is off. If the step counter or its permission is missing, a timer replaces the distance.",
                    "இலக்கு தூரத்தை போனுடன் நடந்து முடியுங்கள். தூரம் போனின் காலடி எண்ணும் சென்சாரிலிருந்து தோராயமாக கணக்கிடப்படும், திரை அணைந்திருந்தாலும் எண்ணப்படும். சென்சார் இல்லையென்றால் அல்லது அனுமதி இயக்கத்தில் இல்லையென்றால், தூரத்துக்கு பதிலாக டைமர் ஓடும்."
                )
            ),
            Situation(
                Icons.Filled.SelfImprovement,
                tr("Meditation and Reading", "தியானம், வாசிப்பு"),
                tr(
                    "A timer runs for the minutes you set. It only runs while the challenge screen is open, and it can't be skipped.",
                    "நீங்கள் அமைத்த நிமிடங்களுக்கு டைமர் ஓடும். சவால் திரை திறந்திருக்கும்போது மட்டுமே ஓடும், அதை தவிர்க்க முடியாது."
                )
            ),
            Situation(
                Icons.Filled.Replay,
                tr("Need to step away?", "சற்று வெளியே செல்ல வேண்டுமா?"),
                tr(
                    "In an emergency you can leave the app for up to 5 minutes and then continue. If you stay away longer, your progress starts again from the beginning.",
                    "அவசரத்தில் 5 நிமிடம் வரை ஆப்பை விட்டு வெளியே சென்று திரும்பி தொடரலாம். அதைவிட அதிக நேரம் வெளியே இருந்தால் உங்கள் முன்னேற்றம் மீண்டும் ஆரம்பத்திலிருந்து தொடங்கும்."
                ),
                WarningColor
            )
        )
    ),
    // 3 ---------------------------------------------------------------------------------
    GuideSection(
        title = tr("What Happens When You Miss a Challenge?", "சவாலை முடிக்காவிட்டால் என்ன நடக்கும்?"),
        items = listOf(
            Situation(
                Icons.Filled.Warning,
                tr("The day counts as missed", "அந்த நாள் தவறியதாகும்"),
                tr(
                    "If the time limit ends before you finish, that day is marked as missed and your streak goes back to 0.",
                    "முடிப்பதற்குள் நேர வரம்பு முடிந்துவிட்டால், அந்த நாள் தவறியதாக பதிவாகும். தொடர்ச்சியான சாதனை 0 ஆகிவிடும்."
                ),
                Danger
            ),
            Situation(
                Icons.Filled.Lock,
                tr("Your apps stay locked for 24 hours", "ஆப்கள் 24 மணி நேரம் பூட்டியிருக்கும்"),
                tr(
                    "The lock lasts 24 hours from the moment the alarm rang. Phone, Camera and the apps you allowed keep working.",
                    "அலாரம் அடித்த நேரத்திலிருந்து 24 மணி நேரம் வரை ஆப்கள் பூட்டியே இருக்கும். போன், கேமரா, நீங்கள் அனுமதித்த ஆப்கள் தொடர்ந்து வேலை செய்யும்."
                ),
                Danger
            ),
            Situation(
                Icons.Filled.Timer,
                tr("Emergency unlock", "அவசர காலத் திறப்பு"),
                tr(
                    "In a real emergency, open DisciplineX and start the emergency unlock. Keep the app open for 10 minutes (the time pauses whenever you leave the app). Your phone then unlocks for 30 minutes and locks again by itself. The day stays missed and your streak stays at 0.",
                    "உண்மையான அவசரத்தில் DisciplineX-ஐ திறந்து அவசர காலத் திறப்பை தொடங்குங்கள். ஆப்பை 10 நிமிடம் திறந்து வையுங்கள் (வெளியே சென்றால் நேரம் நின்றுவிடும்). பிறகு போன் 30 நிமிடம் திறந்திருக்கும், தானாகவே மீண்டும் பூட்டிக்கொள்ளும். அந்த நாள் தவறியதாகவே இருக்கும், தொடர்ச்சியான சாதனை 0-லேயே இருக்கும்."
                ),
                WarningColor
            ),
            Situation(
                Icons.Filled.Whatshot,
                tr("You can start again", "மீண்டும் தொடங்கலாம்"),
                tr(
                    "Finish your next challenge on time and your streak starts growing again. Your best streak stays saved in History.",
                    "அடுத்த சவாலை நேரத்துக்குள் முடித்தால் தொடர்ச்சியான சாதனை மீண்டும் கூடத் தொடங்கும். அதிகபட்ச தொடர்ச்சியான சாதனை வரலாற்றில் அப்படியே இருக்கும்."
                ),
                Success
            )
        )
    ),
    // 4 ---------------------------------------------------------------------------------
    GuideSection(
        title = tr("Alarm & Security", "அலாரம் மற்றும் பாதுகாப்பு"),
        items = listOf(
            Situation(
                Icons.Filled.Alarm,
                tr("Set up your alarms", "உங்கள் அலாரங்களை அமைத்துக்கொள்ளுங்கள்"),
                tr(
                    "Choose the time, the days it repeats, the exercise, the target and the time limit. You can add as many alarms as you like and switch any of them on or off.",
                    "நேரம், மீண்டும் அடிக்கும் நாட்கள், பயிற்சி, இலக்கு, நேர வரம்பு ஆகியவற்றை தேர்ந்தெடுங்கள். எத்தனை அலாரம் வேண்டுமானாலும் சேர்க்கலாம், எதையும் இயக்கவோ அணைக்கவோ செய்யலாம்."
                )
            ),
            Situation(
                Icons.Filled.Edit,
                tr("Editing and deleting alarms", "அலாரத்தை மாற்றுதல், நீக்குதல்"),
                tr(
                    "You can edit or delete an alarm whenever you like, except when it rings within the next 2 hours or while your phone is locked. This stops you from turning off tomorrow's alarm at the last minute.",
                    "அலாரத்தை எப்போது வேண்டுமானாலும் மாற்றலாம் அல்லது நீக்கலாம். ஆனால் அது அடுத்த 2 மணி நேரத்துக்குள் அடிக்கப்போகும்போதும், போன் பூட்டியிருக்கும்போதும் முடியாது. நாளைய அலாரத்தை கடைசி நேரத்தில் அணைப்பதை இது தடுக்கும்."
                )
            ),
            Situation(
                Icons.Filled.Apps,
                tr("Allowed apps", "அனுமதிக்கப்பட்ட ஆப்கள்"),
                tr(
                    "Phone, Camera, keyboards and system screens always work. You can also allow other apps, like WhatsApp, to stay open while your phone is locked. This list can't be changed while the phone is locked.",
                    "போன், கேமரா, கீபோர்டு, சிஸ்டம் திரைகள் எப்போதும் வேலை செய்யும். WhatsApp போன்ற மற்ற ஆப்களையும் பூட்டியிருக்கும்போது திறந்து வைக்க அனுமதிக்கலாம். போன் பூட்டியிருக்கும்போது இந்த பட்டியலை மாற்ற முடியாது."
                )
            ),
            Situation(
                Icons.Filled.Security,
                tr("Uninstall protection", "நிறுவல் நீக்கப் பாதுகாப்பு"),
                tr(
                    "While a challenge is waiting or missed, DisciplineX can't be uninstalled and the uninstall password stays hidden. When your phone is not locked, you can see the password in DisciplineX Settings and use it to uninstall. It changes every 30 seconds.",
                    "சவால் முடிக்கப்படாமல் இருக்கும்போதோ தவறியிருக்கும்போதோ DisciplineX-ஐ நிறுவல் நீக்க முடியாது, கடவுச்சொல்லும் மறைக்கப்படும். போன் பூட்டியில்லாதபோது DisciplineX அமைப்புகளில் கடவுச்சொல்லை பார்த்து நிறுவல் நீக்கலாம். அது 30 வினாடிக்கு ஒருமுறை மாறும்."
                )
            )
        )
    ),
    // 5 ---------------------------------------------------------------------------------
    GuideSection(
        title = tr("Permissions & Privacy", "அனுமதிகள் மற்றும் தனியுரிமை"),
        intro = tr(
            "Here is why each permission is needed. If one is off, the alarm or the app lock may not work properly. You can fix it in Settings.",
            "ஒவ்வொரு அனுமதியும் ஏன் தேவை என்பது இங்கே. ஏதேனும் ஒன்று இயக்கத்தில் இல்லையென்றால் அலாரம் அல்லது ஆப் பூட்டு சரியாக செயல்படாமல் போகலாம். அதை அமைப்புகளில் சரிசெய்யலாம்."
        ),
        items = listOf(
            Situation(
                Icons.Filled.Notifications,
                tr("Notifications", "அறிவிப்புகள்"),
                tr(
                    "Shows your alarm and challenge reminders.",
                    "அலாரம், சவால் நினைவூட்டல்களை காட்டும்."
                )
            ),
            Situation(
                Icons.Filled.ScreenLockPortrait,
                tr("Alarm over the lock screen", "பூட்டுத் திரையில் அலாரம்"),
                tr(
                    "Lets the alarm screen appear on top while your phone is locked.",
                    "போன் பூட்டியிருக்கும்போதும் அலாரம் திரை மேலே தோன்ற உதவும்."
                )
            ),
            Situation(
                Icons.Filled.CameraAlt,
                tr("Camera", "கேமரா"),
                tr(
                    "Used live to count push-ups, squats and jumping jacks. Pictures are not saved.",
                    "புஷ்-அப்ஸ், ஸ்குவாட்ஸ், ஜம்பிங் ஜாக்ஸ் எண்ண நேரடியாக மட்டுமே பயன்படும். படங்கள் சேமிக்கப்படாது."
                )
            ),
            Situation(
                Icons.Filled.DirectionsWalk,
                tr("Physical activity", "உடல் செயல்பாடு"),
                tr(
                    "Counts your steps for the walking challenge.",
                    "நடைப்பயிற்சி சவாலுக்கு உங்கள் காலடிகளை எண்ணும்."
                )
            ),
            Situation(
                Icons.Filled.BatteryChargingFull,
                tr("Run in background", "பின்னணியில் இயங்க அனுமதி"),
                tr(
                    "Stops your phone's battery saver from stopping the alarm or the app lock.",
                    "போனின் பேட்டரி சேமிப்பு அமைப்பு அலாரத்தையோ ஆப் பூட்டையோ நிறுத்திவிடாமல் தடுக்கும்."
                )
            ),
            Situation(
                Icons.Filled.Accessibility,
                tr("App lock (Accessibility)", "ஆப் பூட்டு (அணுகல் வசதி)"),
                tr(
                    "Checks which app is opening so you can be sent back to your challenge. It also reads the text of the system's uninstall and device admin screens, only to see whether they are about DisciplineX. It does not read your messages or what you type.",
                    "எந்த ஆப் திறக்கிறது என்பதை பார்த்து, உங்களை மீண்டும் சவாலுக்கு அனுப்ப உதவும். சிஸ்டத்தின் நிறுவல் நீக்க திரை, சாதன நிர்வாகி திரை ஆகியவை DisciplineX பற்றியவையா என்பதை அறிய மட்டும் அவற்றில் உள்ள எழுத்தையும் படிக்கும். உங்கள் செய்திகளையோ, நீங்கள் தட்டச்சு செய்வதையோ படிக்காது."
                )
            ),
            Situation(
                Icons.Filled.Security,
                tr("Uninstall protection", "நிறுவல் நீக்கப் பாதுகாப்பு"),
                tr(
                    "Makes it impossible to uninstall DisciplineX in one tap. It does not take control of your phone.",
                    "DisciplineX-ஐ ஒரே தட்டலில் நிறுவல் நீக்க முடியாதபடி செய்யும். உங்கள் போனை கட்டுப்படுத்தாது."
                )
            ),
            Situation(
                Icons.Filled.CloudOff,
                tr("Your data stays on your phone", "உங்கள் தரவு உங்கள் போனிலேயே இருக்கும்"),
                tr(
                    "Alarms, streak and history are stored only on this phone. DisciplineX does not ask for internet permission, and there is no account or sign-in.",
                    "அலாரங்கள், தொடர்ச்சியான சாதனை, வரலாறு இந்த போனில் மட்டுமே சேமிக்கப்படும். DisciplineX இணைய அனுமதியை கேட்காது, கணக்கு அல்லது உள்நுழைவு இல்லை."
                ),
                Success
            ),
            Situation(
                Icons.Filled.Info,
                tr("The lock is not unbreakable", "பூட்டு உடைக்க முடியாதது அல்ல"),
                tr(
                    "The app lock relies on an accessibility service, so it is not the same as phone-level security. A determined person could get around it, for example by switching that service off in the phone's settings.",
                    "ஆப் பூட்டு அணுகல் வசதி சேவையை சார்ந்தது, அதனால் இது போன் அளவிலான பாதுகாப்புக்கு சமமானது அல்ல. உறுதியாக முயன்றால் ஒருவர் இதை தவிர்க்க முடியும், உதாரணமாக போன் அமைப்புகளில் அந்த சேவையை அணைப்பதன் மூலம்."
                ),
                WarningColor
            )
        )
    )
)
