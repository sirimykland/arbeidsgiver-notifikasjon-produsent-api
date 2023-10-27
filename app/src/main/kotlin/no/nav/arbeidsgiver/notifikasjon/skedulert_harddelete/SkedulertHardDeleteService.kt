package no.nav.arbeidsgiver.notifikasjon.skedulert_harddelete

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Health
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Subsystem
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.logger
import java.time.Instant
import java.time.OffsetDateTime
import java.util.*

class SkedulertHardDeleteService(
    private val repo: SkedulertHardDeleteRepository,
    private val hendelseProdusent: HendelseProdusent,
) {
    private val log = logger()

    suspend fun sendSkedulerteHardDeletes(tilOgMed: Instant) {
        val skalSlettes = repo.hentSkedulerteHardDeletes(tilOgMed = tilOgMed)

        skalSlettes.forEach {
            if (it.beregnetSlettetidspunkt > tilOgMed) {
                log.error("Beregnet slettetidspunkt kan ikke være i fremtiden. {}", it.loggableToString())
                Health.subsystemAlive[Subsystem.AUTOSLETT_SERVICE] = false
                return
            }

            hendelseProdusent.send(
                HendelseModel.HardDelete(
                    hendelseId = UUID.randomUUID(),
                    aggregateId = it.aggregateId,
                    virksomhetsnummer = it.virksomhetsnummer,
                    deletedAt = OffsetDateTime.now(),
                    produsentId = it.produsentid,
                    kildeAppNavn = NaisEnvironment.clientId,
                    grupperingsid = if (it.isSak) it.grupperingsid else null,
                    merkelapp = it.merkelapp,
                )
            )
        }
    }

    suspend fun cascadeHardDeletes() {
        val registrerteHardDeletes = repo.finnRegistrerteHardDeletes(100)

        registrerteHardDeletes.forEach { aggregate ->
            if (aggregate.isSak) {
                if (aggregate.grupperingsid == null) {
                    log.error("Sak uten grupperingsid kan ikke slettes. {}", aggregate.loggableToString())
                    Health.subsystemAlive[Subsystem.HARDDELETE_SERVICE] = false
                    return
                }

                repo.hentNotifikasjonerForSak(aggregate.merkelapp, aggregate.grupperingsid)
                    .filter { it.aggregateId != aggregate.aggregateId }
                    .forEach { notifikasjon ->
                        hendelseProdusent.send(
                            HendelseModel.HardDelete(
                                hendelseId = UUID.randomUUID(),
                                aggregateId = notifikasjon.aggregateId,
                                virksomhetsnummer = notifikasjon.virksomhetsnummer,
                                deletedAt = OffsetDateTime.now(),
                                produsentId = notifikasjon.produsentid,
                                kildeAppNavn = NaisEnvironment.clientId,
                                grupperingsid = null,
                                merkelapp = notifikasjon.merkelapp,
                            )
                        )
                }
            }

            repo.hardDelete(aggregate.aggregateId)
        }
    }
}
