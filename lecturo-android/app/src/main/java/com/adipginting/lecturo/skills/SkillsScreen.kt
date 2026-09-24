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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adipginting.lecturo.AppContainer
import com.adipginting.lecturo.domain.skills.Skill
import com.adipginting.lecturo.domain.skills.Skills
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The installed skills, and where more come from. Installing is the whole
 * surface for now: a skill runs against a book once book context can be read.
 */
class SkillsViewModel(app: Application) : AndroidViewModel(app) {
    private val skills: Skills = AppContainer.skills(app)

    val installed: StateFlow<List<Skill>> = skills.observeInstalled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var error by mutableStateOf<String?>(null)
        private set
    var installing by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { runCatching { skills.ensureStarters() } }
    }

    /** Fetches a catalog and installs every manifest that is runnable. */
    fun install(catalogUrl: String) {
        val url = catalogUrl.trim()
        if (url.isEmpty() || installing) return
        viewModelScope.launch {
            installing = true
            error = null
            notice = null
            try {
                val installedCount = SkillCatalog.fetch(url)
                    .mapNotNull { it.toSkill(url) }
                    .onEach { skills.install(it) }
                    .size
                notice = if (installedCount == 0) {
                    "No usable skills in that catalog"
                } else {
                    "Installed $installedCount skill" + if (installedCount == 1) "" else "s"
                }
            } catch (e: Exception) {
                error = e.message ?: "Install failed"
            } finally {
                installing = false
            }
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch { skills.uninstall(id) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(onBack: () -> Unit, vm: SkillsViewModel = viewModel()) {
    val skills by vm.installed.collectAsState()
    var catalogUrl by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Install from a catalog", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = catalogUrl,
                    onValueChange = { catalogUrl = it },
                    label = { Text("Catalog URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { vm.install(catalogUrl) },
                    enabled = !vm.installing && catalogUrl.isNotBlank(),
                ) {
                    Text(if (vm.installing) "Installing…" else "Install")
                }
                vm.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                vm.notice?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(skills, key = { it.id }) { skill ->
                    SkillRow(skill = skill, onUninstall = { vm.uninstall(skill.id) })
                }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: Skill, onUninstall: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
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
                Text(
                    listOfNotNull(
                        skill.author?.takeIf { it.isNotBlank() },
                        skill.scope.takeIf { it.isNotBlank() },
                        skill.catalogUrl?.takeIf { it.isNotBlank() }?.let { "Installed" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onUninstall) {
                Icon(Icons.Default.Delete, contentDescription = "Uninstall ${skill.name}")
            }
        }
    }
}
