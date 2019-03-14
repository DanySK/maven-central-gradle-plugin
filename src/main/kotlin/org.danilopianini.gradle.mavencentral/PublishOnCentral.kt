package org.danilopianini.gradle.mavencentral

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginCollection
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.Sign
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.lang.IllegalStateException

const val publicationName = "mavenCentral"
private inline fun <reified T> Project.extension(): T = project.extensions.getByType(T::class.java)
private inline fun <reified T> Project.createExtension(name: String, vararg args: Any?): T = project.extensions.create(name, T::class.java, *args)
private inline fun <reified S, reified T: Plugin<S>> Project.plugin(): PluginCollection<T> = project.plugins.withType(T::class.java)
private inline fun <reified T> Project.configure(crossinline  body: T.() -> Unit): Unit =
    project.extensions.configure(T::class.java) { it.body() }
private inline fun <reified T: Task> Project.configureTask(crossinline  body: T.() -> Unit) =
    project.tasks.withType(T::class.java) { it.body() }
private operator fun <T> Property<T>.invoke(): T = get()

class PublishOnCentral : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withType(JavaPlugin::class.java) {
            project.plugins.withType(MavenPublishPlugin::class.java) {
                val extension = project.createExtension<PublishOnCentralExtension>(PublishOnCentralExtension.extensionName, project)
                project.tasks.register("sourcesJar", SourcesJar::class.java)
                project.tasks.register("javadocJar", JavadocJar::class.java)
                project.configure<PublishingExtension> {
                    publications { publications ->
                        publications.create(publicationName, MavenPublication::class.java) { with(it){
                            val javaComponent = project.components.find { it.name == "java" }
                                ?: throw IllegalStateException("Cannot find Java project component.")
                            println(javaComponent)
                            from(javaComponent)
                            artifact(project.property("sourcesJar"))
                            artifact(project.property("javadocJar"))
                            pom { with(it) {
                                name.set(extension.artifactId())
                                description.set(extension.projectDescription())
                                packaging = "jar"
                                val ref = "${extension.scmRootUrl()}/${extension.artifactId()}"
                                url.set(ref)
                                licenses {
                                    it.license { license ->
                                        license.name.set(extension.licenseName())
                                        license.url.set(extension.licenseUrl())
                                    }
                                }
                                scm { scm ->
                                    scm.url.set(ref)
                                    val scmConnection = "${extension.scmType()}:${extension.scmLogin()}/${extension.scmRepoName()}"
                                    scm.connection.set(scmConnection)
                                    scm.developerConnection.set(scmConnection)
                                }
                            }}
                        }}
                    }
                    repositories {
                        it.maven {
                            it.url = extension.repositoryURL()
                            it.credentials {
                                it.username = extension.ossrhUsername()
                                it.password = extension.ossrhPassword()
                            }
                        }
                    }
                }
                project.plugins.withType(SigningPlugin::class.java) {
                    project.configure<SigningExtension> {
                        sign(project.extension<PublishingExtension>().publications.getByName(publicationName))
                    }
                    project.configureTask<Sign> {
                        onlyIf { extension.signArchives.get() }
                    }
                }
            }
        }
    }
}

open class JarWithClassifier(classifier: String) : Jar() {
    init {
        archiveClassifier.set(classifier)
    }
}

open class SourcesJar: JarWithClassifier("sources") {
    init {
        val sourceSets = project.properties["sourceSets"] as? SourceSetContainer
            ?: throw IllegalStateException("Unable to get sourceSets for project $project. Got ${project.properties["sourceSets"]}")
        val main = sourceSets.getByName("main").allSource
        from(main)
    }
}

open class JavadocJar: JarWithClassifier("javadoc") {
    init {
        val javadoc = project.tasks.findByName("javadoc") as? Javadoc
            ?: throw IllegalStateException("Unable to get javadoc task for project $project. Got ${project.task("javadoc")}")
        from(javadoc.destinationDir)
    }
}