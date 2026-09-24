package com.adipginting.lecturo.skills

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.domain.skills.Insight
import com.adipginting.lecturo.domain.skills.Skill
import com.adipginting.lecturo.domain.skills.Skills
import com.adipginting.lecturo.util.MarkdownText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The skills a book has applied, run on demand, and what each run produced. A run
 * reads the book's own text, so only EPUBs can run for now.
 */
class DocumentSkillsViewModel(app: Application, private val docId: String) : AndroidViewModel(app) {
    private val skills: Skills = AppContainer.skills(app)
    private val runner = SkillRun(app)

    val installed: StateFlow<List<Skill>> = skills.observeInstalled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val applied: StateFlow<Set<String>> = skills.observeApplied(docId)
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val insights: StateFlow<List<Insight>> = skills.observeRuns(docId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The skill id currently running, if any. */
    var running by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { runCatching { skills.ensureStarters() } }
    }

    fun toggle(skill: Skill) {
        viewModelScope.launch {
            if (applied.value.contains(skill.id)) skills.unapply(docId, skill.id)
            else skills.apply(docId, skill.id)
        }
    }

    fun run(skill: Skill) {
        if (running != null) return
        viewModelScope.launch {
            running = skill.id
            error = null
            try {
                runner.run(docId, skill)
            } catch (e: Exception) {
                error = e.message ?: "The run failed"
            } finally {
                running = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentSkillsScreen(
    docId: String,
    onBack: () -> Unit,
    vm: DocumentSkillsViewModel = viewModel(
        key = "skills-$docId",
        factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                DocumentSkillsViewModel(app, docId)
            }
        },
    ),
) {
    val skills by vm.installed.collectAsState()
    val applied by vm.applied.collectAsState()
    val insights by vm.insights.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills & insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Apply skills", style = MaterialTheme.typography.titleMedium) }
            items(skills, key = { it.id }) { skill ->
                SkillToggleRow(
                    skill = skill,
                    applied = applied.contains(skill.id),
                    running = vm.running == skill.id,
                    onToggle = { vm.toggle(skill) },
                    onRun = { vm.run(skill) },
                )
            }
            vm.error?.let { message ->
                item {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { Text("Insights", style = MaterialTheme.typography.titleMedium) }
            if (insights.isEmpty()) {
                item {
                    Text(
                        "No runs yet. Apply a skill and run it on this book.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(insights, key = { it.id }) { insight -> InsightCard(insight) }
            }
        }
    }
}

@Composable
private fun SkillToggleRow(
    skill: Skill,
    applied: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    onRun: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(skill.name, style = MaterialTheme.typography.titleMedium)
                if (skill.description.isNotBlank()) {
                    Text(
                        skill.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (applied) {
                    TextButton(onClick = onRun, enabled = !running) {
                        if (running) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("Running…")
                        } else {
                            Text("Run")
                        }
                    }
                }
            }
            Switch(checked = applied, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (insight.status) {
                    Insight.Status.Running -> "Running…"
                    Insight.Status.Done -> "Done"
                    Insight.Status.Failed -> "Failed"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when (insight.status) {
                    Insight.Status.Failed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
            insight.result?.let {
                MarkdownText(markdown = it, style = MaterialTheme.typography.bodyMedium)
            }
            insight.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
