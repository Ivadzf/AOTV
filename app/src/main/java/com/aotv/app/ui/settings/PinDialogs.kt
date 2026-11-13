package com.aotv.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aotv.app.ui.theme.Accent
import com.aotv.app.ui.theme.BackgroundCard
import com.aotv.app.ui.theme.Border
import com.aotv.app.ui.theme.DangerText
import com.aotv.app.ui.theme.TextPrimary
import com.aotv.app.ui.theme.TextSecondary

/** Pede o PIN pra confirmar uma ação (sair do infantil, abrir Addons/TMDB/Aparelho, ou entrar num perfil com PIN). */
@Composable
fun VerifyPinDialog(title: String = "PIN dos pais", onConfirm: (pin: String) -> Boolean, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Digite o PIN pra continuar.", color = TextSecondary, fontSize = 13.sp)
                com.aotv.app.ui.common.TvTextFieldGate { gate ->
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(4); wrong = false },
                        label = { Text("PIN (4 dígitos)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = wrong,
                        modifier = gate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                }
                if (wrong) Text("PIN incorreto.", color = DangerText, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onConfirm(pin)) Unit else wrong = true }, enabled = pin.length == 4) {
                Text("Confirmar", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = TextSecondary) } },
    )
}

/** Define ou troca o PIN dos pais. Pede 2x pra confirmar que não foi erro de dedo. */
@Composable
fun SetPinDialog(title: String = "Definir PIN dos pais", onSet: (pin: String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.length == 4 && pin != confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Esse PIN é pedido pra sair do perfil infantil e pra abrir Addons, a chave do TMDB e o Aparelho nas Configurações.",
                    color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                )
                com.aotv.app.ui.common.TvTextFieldGate { gate ->
                    OutlinedTextField(
                        value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                        label = { Text("Novo PIN (4 dígitos)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = gate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                }
                com.aotv.app.ui.common.TvTextFieldGate { gate ->
                    OutlinedTextField(
                        value = confirm, onValueChange = { confirm = it.filter(Char::isDigit).take(4) },
                        label = { Text("Confirme o PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = mismatch,
                        modifier = gate,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = Border,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent,
                        ),
                    )
                }
                if (mismatch) Text("Os PINs não batem.", color = DangerText, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(pin) }, enabled = pin.length == 4 && pin == confirm) {
                Text("Salvar", color = Accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não", color = TextSecondary) } },
    )
}
