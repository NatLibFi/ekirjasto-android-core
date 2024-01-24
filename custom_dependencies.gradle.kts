configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("org.thepalaceproject.theme:org.thepalaceproject.theme.core"))
            .using(project(":ekirjasto-theme:core"))
            .because("ekirjasto ui requirements")
    }
}