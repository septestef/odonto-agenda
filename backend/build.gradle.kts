plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ar.turnotarde"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	// C-03: el starter activa sesión, CSRF y autorización; spring-security-crypto llega transitiva.
	implementation("org.springframework.boot:spring-boot-starter-security")
	runtimeOnly("org.postgresql:postgresql")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Unitarios (*Test) e integración (*IT) comparten source set; se separan por nombre de clase (D8).
tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	// No heredar la zona horaria de Windows: el negocio trabaja en UTC internamente.
	systemProperty("user.timezone", "UTC")
	testLogging {
		events("failed", "skipped")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
	}
}

tasks.test {
	exclude("**/*IT.class")
}

val integrationTest = tasks.register<Test>("integrationTest") {
	description = "Corre los tests de integración (*IT) contra PostgreSQL local (turnotarde_test)."
	group = "verification"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	include("**/*IT.class")
	shouldRunAfter(tasks.test)
}

tasks.check {
	dependsOn(integrationTest)
}

// Frontend embebido (D10): npm ci -> npm run build -> copia a src/main/resources/static (carpeta generada e ignorada).
// Con -PskipFrontend se omite todo esto para iterar solo el backend.
val frontendDir = layout.projectDirectory.dir("../frontend")
val npmExecutable = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val npmInstall = tasks.register<Exec>("npmInstall") {
	description = "Instala las dependencias del frontend (npm ci)."
	group = "frontend"
	workingDir = frontendDir.asFile
	commandLine(npmExecutable, "ci")
	inputs.files(frontendDir.file("package.json"), frontendDir.file("package-lock.json"))
	outputs.file(frontendDir.file("node_modules/.package-lock.json"))
}

val npmBuild = tasks.register<Exec>("npmBuild") {
	description = "Compila el frontend con Vite (npm run build)."
	group = "frontend"
	dependsOn(npmInstall)
	workingDir = frontendDir.asFile
	commandLine(npmExecutable, "run", "build")
	inputs.dir(frontendDir.dir("src"))
	inputs.files(
		frontendDir.file("index.html"),
		frontendDir.file("package.json"),
		frontendDir.file("package-lock.json"),
		frontendDir.file("tsconfig.json"),
		frontendDir.file("vite.config.ts"),
	)
	outputs.dir(frontendDir.dir("dist"))
}

val copyFrontend = tasks.register<Sync>("copyFrontend") {
	description = "Copia el build del frontend a src/main/resources/static."
	group = "frontend"
	from(npmBuild)
	into(layout.projectDirectory.dir("src/main/resources/static"))
}

if (!project.hasProperty("skipFrontend")) {
	tasks.processResources {
		dependsOn(copyFrontend)
	}
}
