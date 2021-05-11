package no.nav.arbeidsgiver.notifikasjon

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beBlank
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.Altinn
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.GraphQLRequest
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.createDataSource
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime

@ExperimentalTime
class BrukerApiTests : DescribeSpec({
    val altinn = object : Altinn {
        override fun hentAlleTilganger(fnr: String, selvbetjeningsToken: String) = listOf<Tilgang>()
    }

    val engine by ktorEngine(
        brukerGraphQL = createBrukerGraphQL(
            altinn = altinn,
            dataSourceAsync = CompletableFuture.completedFuture(runBlocking { createDataSource() }),
            kafkaProducer = mockk()
        ),
        produsentGraphQL = createProdusentGraphQL(
            kafkaProducer = mockk()
        )
    )

    describe("POST bruker-api /api/graphql") {
        lateinit var response: TestApplicationResponse
        lateinit var query: String
        val beskjed = QueryBeskjedMedId(
            merkelapp = "foo",
            tekst = "",
            grupperingsid = "",
            lenke = "",
            eksternId = "",
            mottaker = FodselsnummerMottaker("00000000000", "43"),
            opprettetTidspunkt = OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
            id = "1"
        )

        beforeEach {
            mockkObject(QueryModelRepository)
            coEvery {
                QueryModelRepository.hentNotifikasjoner(any(), any(), any())
            } returns listOf(beskjed)

            response = engine.post("/api/graphql",
                host = BRUKER_HOST,
                jsonBody = GraphQLRequest(query),
                accept = "application/json",
                authorization = "Bearer $SELVBETJENING_TOKEN"
            )
        }
        afterEach {
            unmockkObject(QueryModelRepository)
        }
        context("Query.notifikasjoner") {
            query = """
                {
                    notifikasjoner {
                        ...on Beskjed {
                            lenke
                            tekst
                            merkelapp
                            opprettetTidspunkt
                            id
                        }
                    }
                }
            """.trimIndent()

            it("status is 200 OK") {
                response.status() shouldBe HttpStatusCode.OK
            }
            it("response inneholder ikke feil") {
                response.getGraphqlErrors() should beEmpty()
            }


            context("respons er parsed som liste av Beskjed") {
                lateinit var resultat: List<Beskjed>

                beforeEach {
                    resultat = response.getTypedContent("notifikasjoner")
                }

                it("returnerer beskjeden fra repo") {
                    resultat shouldNot beEmpty()
                    resultat[0].merkelapp shouldBe beskjed.merkelapp
                    resultat[0].id shouldNot beBlank()
                    resultat[0].id shouldBe "1"
                }
            }
        }
    }
})
