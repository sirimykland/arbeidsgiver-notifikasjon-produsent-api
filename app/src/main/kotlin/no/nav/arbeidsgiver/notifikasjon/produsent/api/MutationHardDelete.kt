package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.Hendelse
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.coDataFetcher
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.getTypedArgument
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.resolveSubtypes
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.wire
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.CoroutineKafkaProducer
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.KafkaKey
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.sendHendelse
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*

class MutationHardDelete(
    private val kafkaProducer: CoroutineKafkaProducer<KafkaKey, Hendelse>,
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<HardDeleteNotifikasjonResultat>()

        runtime.wire("Mutation") {
            coDataFetcher("hardDeleteNotifikasjon") { env ->
                hardDelete(
                    context = env.getContext(),
                    id = env.getTypedArgument("id")
                )
            }
            coDataFetcher("hardDeleteNotifikasjonByEksternId") { env ->
                hardDelete(
                    context = env.getContext(),
                    eksternId = env.getTypedArgument("eksternId"),
                    merkelapp = env.getTypedArgument("merkelapp"),
                )
            }
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface HardDeleteNotifikasjonResultat

    @JsonTypeName("HardDeleteNotifikasjonVellykket")
    data class HardDeleteNotifikasjonVellykket(
        val id: UUID
    ) : HardDeleteNotifikasjonResultat

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        id: UUID,
    ): HardDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, id) { error -> return error }
        return hardDelete(context, notifikasjon)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        eksternId: String,
        merkelapp: String,
    ): HardDeleteNotifikasjonResultat {
        val notifikasjon = hentNotifikasjon(produsentRepository, eksternId, merkelapp) { error -> return error }
        return hardDelete(context, notifikasjon)
    }

    private suspend fun hardDelete(
        context: ProdusentAPI.Context,
        notifikasjon: ProdusentModel.Notifikasjon,
    ): HardDeleteNotifikasjonResultat {
        val produsent = hentProdusent(context) { error -> return error }
        tilgangsstyrMerkelapp(produsent, notifikasjon.merkelapp) { error -> return error }

        val hardDelete = Hendelse.HardDelete(
            hendelseId = UUID.randomUUID(),
            notifikasjonId = notifikasjon.id,
            virksomhetsnummer = notifikasjon.virksomhetsnummer,
            deletedAt = OffsetDateTime.now(),
            produsentId = produsent.id,
            kildeAppNavn = context.appName
        )

        kafkaProducer.sendHendelse(hardDelete)
        produsentRepository.oppdaterModellEtterHendelse(hardDelete)
        return HardDeleteNotifikasjonVellykket(notifikasjon.id)
    }
}