package demo

import kotlinx.coroutines.runBlocking
import org.springframework.boot.WebApplicationType
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.core.io.ClassPathResource
import org.springframework.fu.kofu.application
import org.springframework.fu.kofu.webflux.webFlux

import org.springframework.data.r2dbc.core.DatabaseClient
import org.springframework.data.r2dbc.core.asType
import org.springframework.data.r2dbc.core.await
import org.springframework.data.r2dbc.core.flow
import org.springframework.data.r2dbc.core.into
import org.springframework.fu.kofu.r2dbc.r2dbcH2
import org.springframework.web.reactive.function.server.*
import java.net.URI

val app = application(WebApplicationType.REACTIVE) {
    beans {
        bean<BarRepository>()
    }

    listener<ApplicationReadyEvent> {
        runBlocking {
            ref<BarRepository>().init()
        }
    }

    r2dbcH2()

    webFlux {
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080

        coRouter {
            val repository = ref<BarRepository>()

            GET("/") { permanentRedirect(URI("/index.html")).buildAndAwait() }

            "/bars".nest {
                GET("/") { ok().json().bodyAndAwait<Bar>(repository.findAll()) }
                POST("/") { request ->
                    // val bar = request.awaitBody<Bar>()
                    val name = request.awaitFormData()["name"]?.firstOrNull()
                    if (name == null) {
                        badRequest().buildAndAwait()
                    }
                    else {
                        repository.save(Bar(name))
                        seeOther(URI("/")).buildAndAwait()
                    }
                }
            }

            resources("/**", ClassPathResource("static/"))
        }

        codecs {
            resource()
            string()
            jackson()
            form()
        }

    }

}

data class Bar(val name: String)

class BarRepository(private val client: DatabaseClient) {

    fun findAll() =
            client.select().from("bars").asType<Bar>().fetch().flow()

    suspend fun save(bar: Bar) =
            client.insert().into<Bar>().table("bars").using(bar).await()

    suspend fun init() =
            client.execute("CREATE TABLE IF NOT EXISTS bars (name varchar);").await()
}

fun main() {
    app.run()
}
