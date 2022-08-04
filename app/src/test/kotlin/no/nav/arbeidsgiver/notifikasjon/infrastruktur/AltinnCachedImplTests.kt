package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.*
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.AltinnReportee
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceCode
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.ServiceEdition
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.produsenter.ServicecodeDefinisjon

class AltinnCachedImplTests : DescribeSpec({
    val klient = mockk<SuspendingAltinnClient>()
    describe("AltinnCachedImpl#hentTilganger") {
        val def = ServicecodeDefinisjon("1", "1")
        val fnr = "42"
        val virksomhet1 = AltinnReportee(
            name = "virksomhet1",
            type = "Business",
            organizationNumber = "1",
            parentOrganizationNumber = null,
            organizationForm = null,
            status = null,
            socialSecurityNumber = null
        )
        val virksomhet2 = AltinnReportee(
            name = "virksomhet2",
            type = "Business",
            organizationNumber = "2",
            parentOrganizationNumber = null,
            organizationForm = null,
            status = null,
            socialSecurityNumber = null
        )
        beforeContainer {
            clearMocks(klient)
            coEvery {
                klient.hentOrganisasjoner(
                    any(),
                    Subject(fnr),
                    ServiceCode(def.code),
                    ServiceEdition(def.version),
                    false
                )
            } returns listOf(virksomhet1)
            coEvery {
                klient.hentOrganisasjoner(
                    any(),
                    Subject(fnr),
                    true
                )
            } returns listOf(virksomhet2)
            // TODO: legg til roller
        }

        context("når cache er disabled") {
            val cachedAltinn = AltinnCachedImpl(
                klient = klient,
                maxCacheSize = 0,
            )

            val tilganger = cachedAltinn.hentTilganger(fnr, "token", listOf(def), listOf())
            it("returnerer tilganger") {
                tilganger.tjenestetilganger + tilganger.reportee + tilganger.rolle shouldContainExactlyInAnyOrder listOf(
                    BrukerModel.Tilgang.Altinn("1", def.code, def.version),
                    BrukerModel.Tilgang.AltinnReportee(fnr = fnr, virksomhet = "2")
                )
            }

            val tilganger2 = cachedAltinn.hentTilganger(fnr, "token", listOf(def), listOf())
            it("returnerer samme tilganger for samme kall") {
                tilganger shouldBe tilganger2
            }
            it("altinn klient ble invokert for hvert kall til tjenesten (cache miss)") {
                coVerify(exactly = 2) {
                    klient.hentOrganisasjoner(
                        any(),
                        Subject(fnr),
                        ServiceCode(def.code),
                        ServiceEdition(def.version),
                        false
                    )
                }
            }
        }

        context("når cache er enablet") {
            val cachedAltinn = AltinnCachedImpl(klient = klient)

            val tilganger = cachedAltinn.hentTilganger(fnr, "token", listOf(def), listOf())
            it("returnerer tilganger") {
                tilganger.tjenestetilganger + tilganger.reportee + tilganger.rolle shouldContainExactlyInAnyOrder listOf(
                    BrukerModel.Tilgang.Altinn("1", def.code, def.version),
                    BrukerModel.Tilgang.AltinnReportee(fnr = fnr, virksomhet = "2")
                )
            }

            val tilganger2 = cachedAltinn.hentTilganger(fnr, "token", listOf(def), listOf())
            it("returnerer samme tilganger for samme kall") {
                tilganger shouldBe tilganger2
            }
            it("altinn klient ble kun invokert for første kall (cache hit)") {
                coVerify(exactly = 1) {
                    klient.hentOrganisasjoner(
                        any(),
                        Subject(fnr),
                        ServiceCode(def.code),
                        ServiceEdition(def.version),
                        false
                    )
                }
            }
        }
    }
})


