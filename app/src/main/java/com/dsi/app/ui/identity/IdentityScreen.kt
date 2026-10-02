package com.dsi.app.ui.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dsi.app.core.Identity

/**
 * Shown once, after the first successful tap sequence. Choosing a name here does not grant
 * that identity — it only decides which account to sign in to. The passphrase and the
 * server-side role claim do the actual work.
 */
@Composable
fun IdentityScreen(
    viewModel: IdentityViewModel,
    onAuthenticated: (Identity) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var revealPassphrase by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Who is using this phone?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "This choice is saved to the phone and is not changed later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Identity.entries.forEach { identity ->
            val selected = state.selected == identity
            OutlinedButton(
                onClick = { viewModel.select(identity) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                )
            ) {
                Text(
                    identity.displayName,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.passphrase,
            onValueChange = viewModel::onPassphraseChange,
            label = { Text("Passphrase") },
            singleLine = true,
            enabled = state.selected != null && !state.busy,
            visualTransformation = if (revealPassphrase) VisualTransformation.None
            else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.submit(onAuthenticated) }),
            trailingIcon = {
                TextButton(onClick = { revealPassphrase = !revealPassphrase }) {
                    Text(if (revealPassphrase) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { viewModel.submit(onAuthenticated) },
            enabled = state.selected != null && !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Continue")
            }
        }
    }
}
