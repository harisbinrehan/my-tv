plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies { implementation(project(":atvremote")) }

application { mainClass.set("com.shoqremote.cli.MainKt") }
