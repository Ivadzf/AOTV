package com.aotv.app.domain.model

/** Membro do elenco (ator + personagem) de um filme/série. */
data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val photoUrl: String,
)
