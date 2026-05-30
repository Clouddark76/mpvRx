package app.gyrolet.mpvrx.ui.preferences

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.gyrolet.mpvrx.preferences.AdvancedPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.presentation.Screen
import app.gyrolet.mpvrx.presentation.components.ConfirmDialog
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.editor.MpvHelpScreen
import app.gyrolet.mpvrx.ui.editor.MpvScriptEditor
import app.gyrolet.mpvrx.ui.utils.LocalBackStack
import app.gyrolet.mpvrx.ui.utils.popSafely
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
data class LuaScriptEditorScreen(
  val scriptName: String?
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val scope = rememberCoroutineScope()

    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()

    val isNewScript = scriptName == null

    var scriptContent by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf(scriptName?.substringBeforeLast('.') ?: "") }
    var scriptExtension by remember {
      mutableStateOf(
        scriptName
          ?.substringAfterLast('.', "lua")
          ?.lowercase()
          ?.takeIf { it == "lua" || it == "js" }
          ?: "lua",
      )
    }
    var hasUnsavedChanges by remember { mutableStateOf(isNewScript) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Helper: resolve the scripts subdirectory as a File, or null
    fun resolveScriptsDirAsFile(storageLocation: String): File? {
      val rootDir = File(storageLocation)
      if (!rootDir.exists() || !rootDir.isDirectory || !rootDir.canRead()) return null
      return rootDir.listFiles()?.firstOrNull {
        it.isDirectory && it.name.equals("scripts", ignoreCase = true)
      } ?: rootDir
    }

    // Load script content if editing existing script
    LaunchedEffect(scriptName, mpvConfStorageLocation) {
      if (scriptName == null || mpvConfStorageLocation.isBlank()) return@LaunchedEffect
      withContext(Dispatchers.IO) {
        var content: String? = null

        // Intento 1: path de archivo directo
        val scriptsDir = resolveScriptsDirAsFile(mpvConfStorageLocation)
        if (scriptsDir != null) {
          val file = File(scriptsDir, scriptName)
          if (file.exists() && file.canRead()) content = file.readText()
        }

        // Intento 2: fallback SAF
        if (content == null) {
          val tempFile = kotlin.io.path.createTempFile()
          runCatching {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree != null && tree.exists()) {
              val safScriptsDir = tree.listFiles().firstOrNull {
                it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
              } ?: tree
              val scriptFile = safScriptsDir.findFile(scriptName)
              if (scriptFile != null && scriptFile.exists()) {
                context.contentResolver.openInputStream(scriptFile.uri)?.copyTo(tempFile.outputStream())
                content = tempFile.readLines().joinToString("\n")
              }
            }
          }
          tempFile.deleteIfExists()
        }

        content?.let { loaded ->
          withContext(Dispatchers.Main) {
            scriptContent = loaded
            hasUnsavedChanges = false
          }
        }
      }
    }

    fun saveScript() {
      if (fileName.isBlank()) {
        Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
        return
      }

      val finalFileName = "$fileName.$scriptExtension"

      scope.launch(Dispatchers.IO) {
        try {
          if (mpvConfStorageLocation.isBlank()) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "No storage location set", Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          var saved = false

          // Intento 1: path de archivo directo
          val scriptsDir = resolveScriptsDirAsFile(mpvConfStorageLocation)
          if (scriptsDir != null) {
            // Si renombra, elimina el archivo viejo
            if (!isNewScript && scriptName != null && scriptName != finalFileName) {
              File(scriptsDir, scriptName).takeIf { it.exists() }?.delete()
            }
            val outFile = File(scriptsDir, finalFileName)
            outFile.writeText(scriptContent)
            saved = true
          }

          // Intento 2: fallback SAF
          if (!saved) {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree == null) {
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "No storage location set", Toast.LENGTH_LONG).show()
              }
              return@launch
            }

            val safScriptsDir = tree.listFiles().firstOrNull {
              it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
            } ?: tree

            // Si renombra, elimina el archivo viejo
            if (!isNewScript && scriptName != null && scriptName != finalFileName) {
              safScriptsDir.findFile(scriptName)?.delete()
            }

            val existing = safScriptsDir.findFile(finalFileName)
            val scriptFile = existing ?: safScriptsDir.createFile("text/plain", finalFileName)
              ?.also { it.renameTo(finalFileName) }
            val uri = scriptFile?.uri ?: run {
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to create file", Toast.LENGTH_LONG).show()
              }
              return@launch
            }

            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
              out.write(scriptContent.toByteArray())
              out.flush()
            } ?: run {
              withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to open output stream", Toast.LENGTH_LONG).show()
              }
              return@launch
            }
          }

          withContext(Dispatchers.Main) {
            hasUnsavedChanges = false
            Toast.makeText(context, "$finalFileName saved successfully", Toast.LENGTH_SHORT).show()
            backStack.popSafely()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    fun shareScript() {
      if (isNewScript) {
        Toast.makeText(context, "Save the script first before sharing", Toast.LENGTH_SHORT).show()
        return
      }

      scope.launch(Dispatchers.IO) {
        try {
          val cacheFile = File(context.cacheDir, scriptName!!)
          var ready = false

          // Intento 1: path de archivo directo
          val scriptsDir = resolveScriptsDirAsFile(mpvConfStorageLocation)
          if (scriptsDir != null) {
            val file = File(scriptsDir, scriptName)
            if (file.exists() && file.canRead()) {
              file.copyTo(cacheFile, overwrite = true)
              ready = true
            }
          }

          // Intento 2: fallback SAF
          if (!ready) {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree != null && tree.exists()) {
              val safScriptsDir = tree.listFiles().firstOrNull {
                it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
              } ?: tree
              val scriptFile = safScriptsDir.findFile(scriptName)
              if (scriptFile != null && scriptFile.exists()) {
                context.contentResolver.openInputStream(scriptFile.uri)?.use { input ->
                  cacheFile.outputStream().use { output -> input.copyTo(output) }
                }
                ready = true
              }
            }
          }

          if (!ready) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "Script file not found", Toast.LENGTH_SHORT).show()
            }
            return@launch
          }

          val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile,
          )

          withContext(Dispatchers.Main) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_STREAM, contentUri)
              putExtra(Intent.EXTRA_SUBJECT, scriptName)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share $scriptName"))
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    fun deleteScript() {
      if (isNewScript) {
        backStack.popSafely()
        return
      }

      scope.launch(Dispatchers.IO) {
        try {
          var deleted = false

          // Intento 1: path de archivo directo
          val scriptsDir = resolveScriptsDirAsFile(mpvConfStorageLocation)
          if (scriptsDir != null) {
            val file = File(scriptsDir, scriptName!!)
            if (file.exists()) {
              deleted = file.delete()
            }
          }

          // Intento 2: fallback SAF
          if (!deleted) {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree != null && tree.exists()) {
              val safScriptsDir = tree.listFiles().firstOrNull {
                it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
              } ?: tree
              val scriptFile = safScriptsDir.findFile(scriptName!!)
              if (scriptFile != null && scriptFile.exists()) {
                deleted = scriptFile.delete()
              }
            }
          }

          if (deleted) {
            val selectedScripts = preferences.selectedLuaScripts.get()
            if (selectedScripts.contains(scriptName)) {
              preferences.selectedLuaScripts.set(selectedScripts - scriptName!!)
            }
            withContext(Dispatchers.Main) {
              Toast.makeText(context, "$scriptName deleted", Toast.LENGTH_SHORT).show()
              backStack.popSafely()
            }
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      TopAppBar(
        title = {
          Column {
            androidx.compose.foundation.text.BasicTextField(
              value = fileName,
              onValueChange = {
                fileName = it
                hasUnsavedChanges = true
              },
              textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              ),
              cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
              decorationBox = { innerTextField ->
                Box {
                  if (fileName.isEmpty()) {
                    Text(
                      text = "Script name",
                      style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                      )
                    )
                  }
                  innerTextField()
                }
              }
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(top = 4.dp),
            ) {
              ScriptExtensionChip(
                label = "Lua",
                selected = scriptExtension == "lua",
                onClick = {
                  if (scriptExtension != "lua") {
                    scriptExtension = "lua"
                    hasUnsavedChanges = true
                  }
                },
              )
              ScriptExtensionChip(
                label = "JS",
                selected = scriptExtension == "js",
                onClick = {
                  if (scriptExtension != "js") {
                    scriptExtension = "js"
                    hasUnsavedChanges = true
                  }
                },
              )
            }
            if (hasUnsavedChanges) {
              Text(
                text = "Unsaved changes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = { backStack.popSafely() }) {
            Icon(
              Icons.Default.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.secondary,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { backStack.add(MpvHelpScreen()) },
            modifier = Modifier.padding(end = 4.dp).size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
              contentColor = MaterialTheme.colorScheme.secondary,
            ),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "Help",
            )
          }

          if (!isNewScript) {
            IconButton(
              onClick = { shareScript() },
              modifier = Modifier.padding(horizontal = 4.dp).size(40.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(Icons.Default.Share, contentDescription = "Share")
            }
          }

          if (!isNewScript) {
            IconButton(
              onClick = { showDeleteDialog = true },
              modifier = Modifier.padding(horizontal = 4.dp).size(40.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
          }

          IconButton(
            onClick = { saveScript() },
            enabled = hasUnsavedChanges && fileName.isNotBlank(),
            modifier = Modifier.padding(horizontal = 4.dp).size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
              },
              contentColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
              disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
              disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
            shape = RoundedCornerShape(8.dp),
          ) {
            Icon(
              painter = painterResource(R.drawable.ic_material_symbols_check),
              contentDescription = "Save",
            )
          }
        },
      )

      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
          .imePadding()
      ) {
        MpvScriptEditor(
          content = scriptContent,
          onContentChange = {
            scriptContent = it
            hasUnsavedChanges = true
          },
          language = scriptExtension,
          modifier = Modifier.fillMaxSize(),
        )
      }
    }

    if (showDeleteDialog) {
      ConfirmDialog(
        title = "Delete Script?",
        subtitle = "Are you sure you want to delete \"${scriptName ?: fileName}\"? This action cannot be undone.",
        onConfirm = {
          deleteScript()
          showDeleteDialog = false
        },
        onCancel = {
          showDeleteDialog = false
        },
      )
    }
  }
}

@Composable
private fun ScriptExtensionChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.clickable(onClick = onClick),
    shape = RoundedCornerShape(999.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
  ) {
    Text(
      text = label,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
