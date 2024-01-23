configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == ) "org.thepalaceproject.theme" && requested.name == "org.thepalaceproject.theme.core") {
                useTarget(project(":local_theme:core"))
            }
        }
    }
}