package com.muthu.disciplinex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muthu.disciplinex.data.tr
import com.muthu.disciplinex.ui.theme.Background
import com.muthu.disciplinex.ui.theme.Surface
import com.muthu.disciplinex.ui.theme.TextMuted
import com.muthu.disciplinex.ui.theme.TextPrimary

/**
 * "How this app works": every rule of the app written as IF / THEN / ELSE cards,
 * so it is clear what happens in each situation. Read-only; it never touches
 * settings, alarms or the lock.
 *
 * The numbers here (5 minutes away, 10 minutes wait, 30 minutes unlock, 24 hours
 * lock, 2 hours before an alarm) must match ChallengeSession and AlarmScheduler.
 * If you change one of those constants, change the text below too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowAppWorksScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text(tr("How this app works", "இந்த ஆப் எப்படி வேலை செய்கிறது"), fontWeight = FontWeight.Bold) },
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
                    "Short version: finish your challenge and the phone is yours again. Miss it and it stays locked for 24 hours. Here is exactly what happens in each situation.",
                    "சுருக்கமாக: சவாலை முடித்தால் போன் மீண்டும் உங்களுடையது. தவறினால் 24 மணி நேரம் பூட்டியே இருக்கும். ஒவ்வொரு சூழலிலும் என்ன நடக்கும் என்பது கீழே."
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp)
            )

            sections().forEach { section ->
                SettingsSection(section.title) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        section.rules.forEach { RuleCard(it) }
                    }
                }
            }

            Text(
                tr(
                    "Everything stays on your phone. Nothing is uploaded.",
                    "எல்லாமே உங்கள் போனிலேயே இருக்கும். எதுவும் பதிவேற்றப்படாது."
                ),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
            )
        }
    }
}

private data class Rule(
    val iff: String,
    val then: String,
    val otherwise: String? = null,
    val thenTone: PillTone = PillTone.ON,
    val elseTone: PillTone = PillTone.OFF
)

private data class RuleSection(val title: String, val rules: List<Rule>)

/** One situation: IF (what happens), THEN (result) and, when there is one, ELSE (the other result). */
@Composable
private fun RuleCard(rule: Rule) {
    SettingsGroup {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RuleLine(tr("IF", "ஒருவேளை"), PillTone.NEUTRAL, rule.iff, strong = true)
            RuleLine(tr("THEN", "அப்போது"), rule.thenTone, rule.then, strong = false)
            if (rule.otherwise != null) {
                RuleLine(tr("ELSE", "இல்லையெனில்"), rule.elseTone, rule.otherwise, strong = false)
            }
        }
    }
}

@Composable
private fun RuleLine(label: String, tone: PillTone, text: String, strong: Boolean) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.width(104.dp)) { StatusPill(label, tone) }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = if (strong) TextPrimary else TextMuted
        )
    }
}

private fun sections(): List<RuleSection> = listOf(
    RuleSection(
        tr("When the alarm rings", "அலாரம் அடிக்கும்போது"),
        listOf(
            Rule(
                iff = tr("Your alarm rings.", "உங்கள் அலாரம் அடிக்கும்."),
                then = tr(
                    "Your phone locks and the time limit starts. Dismiss only stops the sound, it does not unlock anything.",
                    "போன் பூட்டப்படும், நேர வரம்பு தொடங்கும். அலாரத்தை நிறுத்தினால் ஒலி மட்டுமே நிற்கும், பூட்டு திறக்காது."
                ),
                thenTone = PillTone.NEUTRAL
            ),
            Rule(
                iff = tr(
                    "You finish the challenge inside the time limit.",
                    "நேர வரம்புக்குள் சவாலை முடிக்கிறீர்கள்."
                ),
                then = tr(
                    "Your phone unlocks and the day counts for your streak.",
                    "போன் திறக்கும், அந்த நாள் உங்கள் தொடர் சாதனையில் சேரும்."
                ),
                otherwise = tr(
                    "The time runs out first: the day is missed, your streak goes back to 0 and the phone stays locked for 24 hours.",
                    "அதற்குள் நேரம் முடிந்துவிட்டால்: அந்த நாள் தவறியதாகும், தொடர் சாதனை 0 ஆகும், போன் 24 மணி நேரம் பூட்டியே இருக்கும்."
                )
            ),
            Rule(
                iff = tr(
                    "The same alarm rings twice within a minute (for example after the clock was changed).",
                    "ஒரே அலாரம் ஒரு நிமிடத்துக்குள் இரண்டு முறை அடிக்கிறது (உதாரணம்: கடிகார நேரத்தை மாற்றிய பிறகு)."
                ),
                then = tr(
                    "It counts as one ring. Your running challenge stays as it is and no day is marked missed.",
                    "அது ஒரே முறையாக எடுத்துக்கொள்ளப்படும். நடந்துகொண்டிருக்கும் சவால் அப்படியே இருக்கும், எந்த நாளும் தவறியதாக பதிவாகாது."
                ),
                thenTone = PillTone.NEUTRAL
            ),
            Rule(
                iff = tr(
                    "Your phone is switched off at alarm time.",
                    "அலாரம் நேரத்தில் உங்கள் போன் அணைந்திருக்கிறது."
                ),
                then = tr(
                    "The alarm does not ring and nothing is recorded for that day.",
                    "அலாரம் அடிக்காது, அந்த நாளுக்கு எதுவும் பதிவாகாது."
                ),
                thenTone = PillTone.NEUTRAL
            )
        )
    ),
    RuleSection(
        tr("During the challenge", "சவால் நடக்கும்போது"),
        listOf(
            Rule(
                iff = tr(
                    "You leave the app for 5 minutes or less (an emergency).",
                    "ஆப்பை விட்டு 5 நிமிடம் அல்லது அதற்கும் குறைவாக வெளியே செல்கிறீர்கள் (அவசரம்)."
                ),
                then = tr(
                    "Your progress is kept.",
                    "உங்கள் முன்னேற்றம் அப்படியே இருக்கும்."
                ),
                otherwise = tr(
                    "You stay away for more than 5 minutes: the progress starts again from zero.",
                    "5 நிமிடத்துக்கு மேல் வெளியே இருந்தால்: முன்னேற்றம் மீண்டும் பூஜ்யத்திலிருந்து தொடங்கும்."
                )
            ),
            Rule(
                iff = tr(
                    "Your exercise is Push-ups, Squats or Jumping Jacks.",
                    "உங்கள் பயிற்சி புஷ்-அப்ஸ், ஸ்குவாட்ஸ் அல்லது ஜம்பிங் ஜாக்ஸ்."
                ),
                then = tr(
                    "The camera counts your reps on the phone. Nothing is saved.",
                    "கேமரா உங்கள் முறைகளை போனிலேயே எண்ணும். எதுவும் சேமிக்கப்படாது."
                ),
                thenTone = PillTone.NEUTRAL
            ),
            Rule(
                iff = tr(
                    "Your exercise is Reading or Meditation.",
                    "உங்கள் பயிற்சி வாசிப்பு அல்லது தியானம்."
                ),
                then = tr(
                    "A timer runs, but only while the challenge screen is open. Tapping cannot add progress.",
                    "டைமர் ஓடும், ஆனால் சவால் திரை திறந்திருக்கும்போது மட்டுமே. தட்டுவதால் முன்னேற்றம் கூடாது."
                ),
                thenTone = PillTone.NEUTRAL
            ),
            Rule(
                iff = tr(
                    "Your exercise is Walking.",
                    "உங்கள் பயிற்சி நடைப்பயிற்சி."
                ),
                then = tr(
                    "The distance is measured from your phone's step counter. You finish when you reach the target distance.",
                    "தூரம் உங்கள் போனின் அடி எண்ணியிலிருந்து அளக்கப்படும். இலக்கு தூரத்தை எட்டியதும் சவால் முடியும்."
                ),
                thenTone = PillTone.NEUTRAL
            )
        )
    ),
    RuleSection(
        tr("After a missed challenge", "சவால் தவறிய பிறகு"),
        listOf(
            Rule(
                iff = tr(
                    "You missed a challenge and use Emergency unlock from Home.",
                    "சவாலை தவறவிட்டு, முகப்புத் திரையில் அவசரத் திறப்பை பயன்படுத்துகிறீர்கள்."
                ),
                then = tr(
                    "Keep the app open for 10 minutes (the time pauses whenever you leave the app). Then your phone unlocks for 30 minutes and locks again by itself. The day stays missed and the streak stays 0.",
                    "ஆப்பை 10 நிமிடம் திறந்து வையுங்கள் (வெளியே சென்றால் நேரம் நின்றுவிடும்). பிறகு போன் 30 நிமிடம் திறந்திருக்கும், தானாகவே மீண்டும் பூட்டிக்கொள்ளும். அந்த நாள் தவறியதாகவே இருக்கும், தொடர் சாதனை 0 ஆகவே இருக்கும்."
                ),
                otherwise = tr(
                    "You do not use it: the phone stays locked for the full 24 hours.",
                    "பயன்படுத்தாவிட்டால்: போன் முழுமையாக 24 மணி நேரம் பூட்டியே இருக்கும்."
                ),
                thenTone = PillTone.NEUTRAL
            )
        )
    ),
    RuleSection(
        tr("Alarms and the lock", "அலாரங்கள் & பூட்டு"),
        listOf(
            Rule(
                iff = tr(
                    "An alarm will ring within the next 2 hours.",
                    "ஒரு அலாரம் அடுத்த 2 மணி நேரத்துக்குள் அடிக்கப்போகிறது."
                ),
                then = tr(
                    "It cannot be edited, deleted or switched off. This stops you from turning off tomorrow's alarm at the last minute.",
                    "அதை மாற்றவோ நீக்கவோ அணைக்கவோ முடியாது. நாளைய அலாரத்தை கடைசி நேரத்தில் அணைப்பதை இது தடுக்கும்."
                ),
                otherwise = tr(
                    "It is more than 2 hours away: you can change, switch off or delete it (unless the phone is locked).",
                    "2 மணி நேரத்துக்கு மேல் இருந்தால்: மாற்றலாம், அணைக்கலாம், நீக்கலாம் (போன் பூட்டியிருக்கும்போது தவிர)."
                ),
                thenTone = PillTone.NEUTRAL,
                elseTone = PillTone.ON
            ),
            Rule(
                iff = tr(
                    "Your phone is locked.",
                    "உங்கள் போன் பூட்டியிருக்கிறது."
                ),
                then = tr(
                    "Only Phone, Camera, your allowed apps, keyboards, the home screen and system screens open. Phone Settings is blocked too. Alarms and the allowed-apps list cannot be changed.",
                    "போன், கேமரா, நீங்கள் அனுமதித்த ஆப்கள், கீபோர்டு, ஹோம் திரை, சிஸ்டம் திரைகள் மட்டுமே திறக்கும். போன் செட்டிங்ஸும் தடுக்கப்படும். அலாரங்களையும் அனுமதிக்கப்பட்ட ஆப் பட்டியலையும் மாற்ற முடியாது."
                ),
                otherwise = tr(
                    "It is not locked: every app works normally.",
                    "பூட்டு இல்லையெனில்: எல்லா ஆப்களும் சாதாரணமாக வேலை செய்யும்."
                ),
                thenTone = PillTone.NEUTRAL,
                elseTone = PillTone.ON
            )
        )
    ),
    RuleSection(
        tr("Permissions and protection", "அனுமதிகள் & பாதுகாப்பு"),
        listOf(
            Rule(
                iff = tr(
                    "Every permission in Settings shows On.",
                    "அமைப்புகளில் எல்லா அனுமதிகளும் இயக்கத்தில் உள்ளன."
                ),
                then = tr(
                    "Everything works as described on this page.",
                    "இந்த பக்கத்தில் சொன்னபடி எல்லாம் வேலை செய்யும்."
                ),
                otherwise = tr(
                    "A permission shows Off: the alarm or the app lock may not work properly. Open Settings, find Permissions and tap Enable.",
                    "ஏதேனும் ஒரு அனுமதி இயக்கத்தில் இல்லையெனில்: அலாரம் அல்லது ஆப் பூட்டு சரியாக வேலை செய்யாமல் போகலாம். அமைப்புகளில் அனுமதிகள் பகுதியில் இயக்கு என்பதை தட்டுங்கள்."
                )
            ),
            Rule(
                iff = tr(
                    "You try to uninstall DisciplineX while a challenge is pending, missed or in emergency unlock.",
                    "சவால் காத்திருக்கும்போது, தவறியபோது அல்லது அவசரத் திறப்பின்போது DisciplineX-ஐ நிறுவல் நீக்க முயல்கிறீர்கள்."
                ),
                then = tr(
                    "Uninstall is blocked and the password cannot be seen or used.",
                    "நிறுவல் நீக்கம் தடுக்கப்படும், கடவுச்சொல்லை பார்க்கவோ பயன்படுத்தவோ முடியாது."
                ),
                otherwise = tr(
                    "The phone is not locked: you can see the password in Settings and use it to uninstall (Uninstall protection must be On).",
                    "போன் பூட்டியில்லையெனில்: அமைப்புகளில் கடவுச்சொல்லை பார்த்து நிறுவல் நீக்கலாம் (நிறுவல் நீக்கப் பாதுகாப்பு இயக்கத்தில் இருக்க வேண்டும்)."
                ),
                thenTone = PillTone.NEUTRAL,
                elseTone = PillTone.NEUTRAL
            )
        )
    )
)
