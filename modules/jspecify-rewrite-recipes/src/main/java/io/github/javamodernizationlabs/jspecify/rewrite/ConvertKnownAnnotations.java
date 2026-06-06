package io.github.javamodernizationlabs.jspecify.rewrite;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts well-known legacy nullness annotations (JetBrains, JSR-305, Spring,
 * FindBugs, Checker Framework, RxJava, Reactor, Android, Micrometer) to their
 * JSpecify counterparts via composed {@link ChangeType} recipes.
 *
 * <p>Per spec section 28.2, this recipe only performs 1:1 type substitutions:
 * each mapped legacy annotation is rewritten to its JSpecify counterpart
 * wherever it occurs. The rewrite is purely a type change and is <em>not</em>
 * placement-aware, so ambiguous declaration-vs-type-use placements (for example
 * on arrays or generic type arguments) are converted in place exactly as they
 * were written rather than being skipped. Use the core find/report flow to
 * review such placements after conversion. Aliases, meta-annotations and
 * package-level defaults are intentionally left to manual review.
 */
public class ConvertKnownAnnotations extends Recipe {

    /**
     * Creates a {@code ConvertKnownAnnotations} recipe.
     */
    public ConvertKnownAnnotations() {
    }

    /**
     * Returns the human-readable display name shown for this recipe in
     * OpenRewrite tooling.
     *
     * @return the recipe display name
     */
    @Override
    public String getDisplayName() {
        return "Convert known nullness annotations to JSpecify";
    }

    /**
     * Returns the description explaining which legacy nullness annotations this
     * recipe converts and that the conversion is not placement-aware.
     *
     * @return the recipe description
     */
    @Override
    public String getDescription() {
        return "Rewrites imports and references of well-known legacy nullness annotations "
                + "(JetBrains, JSR-305, Spring, FindBugs, Checker Framework, RxJava, Reactor, "
                + "Android, Micrometer) to their JSpecify counterparts. Each mapped annotation "
                + "is converted in place regardless of placement; ambiguous array/generic "
                + "declaration-vs-type-use placements are not specially skipped. Use the core "
                + "find/report flow to review such placements after conversion.";
    }

    /**
     * Builds the list of sub-recipes that perform the conversion, composing one
     * {@link ChangeType} recipe per legacy-to-JSpecify mapping declared in the
     * default {@link AnnotationCatalog}.
     *
     * @return the composed list of {@link ChangeType} recipes, one per known
     *         annotation mapping
     */
    @Override
    public List<Recipe> getRecipeList() {
        List<Recipe> recipes = new ArrayList<>();
        for (var mapping : AnnotationCatalog.defaults().mappings().entrySet()) {
            recipes.add(new ChangeType(mapping.getKey(), mapping.getValue(), false));
        }
        return recipes;
    }
}
