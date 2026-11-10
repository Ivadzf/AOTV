package com.aotv.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Item da barra de navegação inferior do celular.
 * [route] é a rota de destino. Quando o item está selecionado, [route] == currentRoute.
 */
data class PhoneNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Itens da barra inferior de Filmes e Séries (Início, Descobrir, Minha Lista, Downloads, Busca). */
fun vodNavItems(
    onInicio: () -> Unit,
    onMinhaLista: () -> Unit,
    onBusca: () -> Unit,
    onDownloads: () -> Unit,
    onDescobrir: () -> Unit,
    inicioRoute: String,
    minhaListaRoute: String,
    buscaRoute: String,
    downloadsRoute: String,
    descobrirRoute: String,
): List<PhoneNavItem> = listOf(
    PhoneNavItem(inicioRoute,     "Início",      Icons.Default.Home,           onInicio),
    PhoneNavItem(descobrirRoute,  "Descobrir",   Icons.Default.Explore,        onDescobrir),
    PhoneNavItem(minhaListaRoute, "Minha Lista", Icons.Default.BookmarkBorder, onMinhaLista),
    PhoneNavItem(downloadsRoute,  "Downloads",   Icons.Default.Download,       onDownloads),
    PhoneNavItem(buscaRoute,      "Busca",       Icons.Default.Search,         onBusca),
)
