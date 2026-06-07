plugins {
	id("org.springframework.boot") version "4.0.6" apply false
	id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
	group = "com.allermeal"
	version = "0.0.1-SNAPSHOT"

	repositories {
		mavenCentral()
	}
}

subprojects {
	plugins.withType<JavaPlugin> {
		extensions.configure<JavaPluginExtension> {
			toolchain {
				languageVersion = JavaLanguageVersion.of(21)
			}
		}

		tasks.withType<JavaCompile>().configureEach {
			options.encoding = "UTF-8"
			options.release = 21
		}

		dependencies {
			add("testImplementation", platform("org.junit:junit-bom:5.12.2"))
			add("testImplementation", "org.junit.jupiter:junit-jupiter")
			add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
		}

		tasks.withType<Test>().configureEach {
			useJUnitPlatform()
		}
	}
}
