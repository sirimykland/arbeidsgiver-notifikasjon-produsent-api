package no.nav.arbeidsgiver.notifikasjon.executable.ekstern_varsling

import no.nav.arbeidsgiver.notifikasjon.ekstern_varsling.EksternVarsling

fun main(@Suppress("UNUSED_PARAMETER") args: Array<String>) {
    EksternVarsling.main(
        httpPort = 8085,
    )
}

