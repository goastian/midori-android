package org.midorinext.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;

public final class WithoutTelemetryPlugin implements Plugin<Project> {
    private static final Attribute<Boolean> CLEAN = Attribute.of("midori.withoutTelemetry", Boolean.class);

    @Override
    public void apply(Project project) {
        project.getDependencies().getAttributesSchema().attribute(CLEAN);
        project.getDependencies().getArtifactTypes().maybeCreate("aar").getAttributes().attribute(CLEAN, false);
        project.getDependencies().registerTransform(WithoutTelemetryAarTransform.class, spec -> {
            spec.getFrom().attribute(CLEAN, false).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar");
            spec.getTo().attribute(CLEAN, true).attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar");
        });
        project.getConfigurations().configureEach(configuration -> {
            configuration.exclude(java.util.Map.of("group", "org.mozilla.telemetry"));
            configuration.getAttributes().attribute(CLEAN, true);
        });
        project.getTasks().register("verifyNoGlean", task -> {
            task.setGroup("verification");
            task.setDescription("Verifies that Mozilla's telemetry SDK is excluded from both browser variants");
            task.doLast(ignored -> {
                for (String name : java.util.List.of("originalPlaystoreDebugRuntimeClasspath", "originalPlaystoreReleaseRuntimeClasspath")) {
                    project.getConfigurations().getByName(name).getResolvedConfiguration().getResolvedArtifacts().forEach(artifact -> {
                        if (artifact.getModuleVersion().getId().getGroup().equals("org.mozilla.telemetry")) {
                            throw new org.gradle.api.GradleException("Telemetry dependency remains: " + artifact.getModuleVersion().getId());
                        }
                    });
                }
            });
        });
        project.getTasks().named("check").configure(task -> task.dependsOn("verifyNoGlean"));
    }
}
