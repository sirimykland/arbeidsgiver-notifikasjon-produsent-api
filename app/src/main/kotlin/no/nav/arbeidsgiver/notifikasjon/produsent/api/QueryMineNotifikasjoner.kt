package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.graphql.*
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentModel
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepository
import java.time.OffsetDateTime
import java.util.*


class QueryMineNotifikasjoner(
    private val produsentRepository: ProdusentRepository,
) {
    fun wire(runtime: RuntimeWiring.Builder) {
        runtime.resolveSubtypes<MineNotifikasjonerResultat>()
        runtime.resolveSubtypes<Notifikasjon>()
        runtime.resolveSubtypes<Mottaker>()

        runtime.wire("Query") {
            coDataFetcher("mineNotifikasjoner") { env ->
                mineNotifikasjoner(
                    context = env.getContext(),
                    inputMerkelapp = env.getArgument<String?>("merkelapp"),
                    inputMerkelapper = env.getArgument<List<String>?>("merkelapper"),
                    grupperingsid = env.getArgument<String>("grupperingsid"),
                    first = env.getArgumentOrDefault("first", 1000),
                    after = Cursor(env.getArgumentOrDefault("after", Cursor.empty().value)),
                    env,
                )
            }
        }
    }

    @JsonTypeName("NotifikasjonConnection")
    data class NotifikasjonConnection(
        val edges: List<Edge<Notifikasjon>>,
        val pageInfo: PageInfo
    ) :
        MineNotifikasjonerResultat,
        Connection<Notifikasjon>

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Notifikasjon {
        @JsonTypeName("Beskjed")
        data class Beskjed(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val beskjed: BeskjedData,
            val eksternVarsel: List<EksternVarsel>,
        ) : Notifikasjon() {
            companion object {
                fun fraDomene(beskjed: ProdusentModel.Beskjed): Beskjed {
                    return Beskjed(
                        metadata = Metadata(
                            id = beskjed.id,
                            grupperingsid = beskjed.grupperingsid,
                            eksternId = beskjed.eksternId,
                            opprettetTidspunkt = beskjed.opprettetTidspunkt,
                            softDeletedAt = beskjed.deletedAt,
                        ),
                        mottaker = Mottaker.fraDomene(beskjed.mottaker),
                        beskjed = BeskjedData(
                            merkelapp = beskjed.merkelapp,
                            tekst = beskjed.tekst,
                            lenke = beskjed.lenke,
                        ),
                        eksternVarsel = listOf(),
                    )
                }
            }
        }

        @JsonTypeName("Oppgave")
        data class Oppgave(
            val mottaker: Mottaker,
            val metadata: Metadata,
            val oppgave: OppgaveData,
            val eksternVarsel: List<EksternVarsel>,
        ) : Notifikasjon() {
            @Suppress("unused")
            /* Sendes til produsent */
            enum class Tilstand {
                NY,
                UTFOERT;
            }

            companion object {
                fun fraDomene(oppgave: ProdusentModel.Oppgave): Oppgave {
                    return Oppgave(
                        metadata = Metadata(
                            id = oppgave.id,
                            grupperingsid = oppgave.grupperingsid,
                            eksternId = oppgave.eksternId,
                            opprettetTidspunkt = oppgave.opprettetTidspunkt,
                            softDeletedAt = oppgave.deletedAt
                        ),
                        mottaker = Mottaker.fraDomene(oppgave.mottaker),
                        oppgave = OppgaveData(
                            tilstand = enumValueOf(oppgave.tilstand.name),
                            merkelapp = oppgave.merkelapp,
                            tekst = oppgave.tekst,
                            lenke = oppgave.lenke,
                        ),
                        eksternVarsel = listOf(),
                    )
                }
            }
        }

        companion object {
            fun fraDomene(notifikasjon: ProdusentModel.Notifikasjon): Notifikasjon {
                return when (notifikasjon) {
                    is ProdusentModel.Beskjed -> Beskjed.fraDomene(notifikasjon)
                    is ProdusentModel.Oppgave -> Oppgave.fraDomene(notifikasjon)
                }
            }
        }
    }

    data class NotifikasjonData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    data class EksternVarsel(
        val id: UUID,
        val status: EksternVarselStatus,
    )

    enum class EksternVarselStatus {
        NY,
        UTFOERT,
        FEIL
    }

    @JsonTypeName("Metadata")
    data class Metadata(
        val id: UUID,
        val grupperingsid: String?,
        val eksternId: String,
        val opprettetTidspunkt: OffsetDateTime = OffsetDateTime.now(),
        val softDeletedAt: OffsetDateTime?,
    ) {
        val softDeleted: Boolean
            get() = softDeletedAt != null
    }

    @JsonTypeName("OppgaveData")
    data class OppgaveData(
        val tilstand: Notifikasjon.Oppgave.Tilstand,
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    @JsonTypeName("BeskjedData")
    data class BeskjedData(
        val merkelapp: String,
        val tekst: String,
        val lenke: String,
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class Mottaker {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.Mottaker): Mottaker {
                return when (domene) {
                    is no.nav.arbeidsgiver.notifikasjon.AltinnMottaker -> AltinnMottaker.fraDomene(domene)
                    is no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker -> NærmesteLederMottaker.fraDomene(domene)
                }
            }
        }
    }

    @JsonTypeName("NaermesteLederMottaker")
    data class NærmesteLederMottaker(
        val naermesteLederFnr: String,
        val ansattFnr: String,
        val virksomhetsnummer: String
    ) : Mottaker() {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.NærmesteLederMottaker): NærmesteLederMottaker {
                return NærmesteLederMottaker(
                    naermesteLederFnr = domene.naermesteLederFnr,
                    ansattFnr = domene.ansattFnr,
                    virksomhetsnummer = domene.virksomhetsnummer,
                )
            }
        }
    }

    @JsonTypeName("AltinnMottaker")
    data class AltinnMottaker(
        val serviceCode: String,
        val serviceEdition: String,
        val virksomhetsnummer: String,
    ) : Mottaker() {
        companion object {
            fun fraDomene(domene: no.nav.arbeidsgiver.notifikasjon.AltinnMottaker): AltinnMottaker {
                return AltinnMottaker(
                    serviceCode = domene.serviceCode,
                    serviceEdition = domene.serviceEdition,
                    virksomhetsnummer = domene.virksomhetsnummer
                )
            }
        }
    }


    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed interface MineNotifikasjonerResultat

    private suspend fun mineNotifikasjoner(
        context: ProdusentAPI.Context,
        inputMerkelapp: String?,
        inputMerkelapper: List<String>?,
        grupperingsid: String?,
        first: Int,
        after: Cursor,
        env: DataFetchingEnvironment,
    ): MineNotifikasjonerResultat {

        val produsent = hentProdusent(context) { error -> return error }

        val merkelapper = when {
            inputMerkelapp == null && inputMerkelapper == null ->
                produsent.tillatteMerkelapper
            inputMerkelapp == null && inputMerkelapper != null ->
                inputMerkelapper
            inputMerkelapp != null && inputMerkelapper == null ->
                listOf(inputMerkelapp)
            else ->
                return Error.UgyldigMerkelapp("Kan ikke spesifiesere begge filtrene 'merkelapp' og 'merkelapper'")
        }

        for (merkelapp in merkelapper) {
            tilgangsstyrMerkelapp(produsent, merkelapp) { error -> return error }
        }

        return produsentRepository
            .finnNotifikasjoner(
                merkelapper = merkelapper,
                grupperingsid = grupperingsid,
                antall = first,
                offset = after.offset
            )
            .map(Notifikasjon::fraDomene)
            .let { notifikasjoner ->
                Connection.create(
                    notifikasjoner,
                    env,
                    ::NotifikasjonConnection
                )
            }
    }
}

val QueryMineNotifikasjoner.Notifikasjon.metadata: QueryMineNotifikasjoner.Metadata
    get() = when (this) {
        is QueryMineNotifikasjoner.Notifikasjon.Beskjed -> this.metadata
        is QueryMineNotifikasjoner.Notifikasjon.Oppgave -> this.metadata
    }

val QueryMineNotifikasjoner.Notifikasjon.id: UUID
    get() = this.metadata.id