package io.github.javamodernizationlabs.jspecify.rewrite;

import io.github.javamodernizationlabs.jspecify.AnnotationCatalog;
import org.openrewrite.Recipe;
import org.openrewrite.java.ChangeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts well-known legacy nullness annotations to their JSpecify
 * counterparts via composed {@link ChangeType} recipes.
 *
 * <p>Per spec section 28.2, this recipe only performs <em>semantically
 * safe</em> 1:1 substitutions. Aliases, meta-annotations and package-level
 * defaults are intentionally left to manual review.
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
     * recipe converts and which cases it intentionally leaves unchanged.
     *
     * @return the recipe description
     */
    @Override
    public String getDescription() {
        return "Rewrites imports and references of well-known legacy nullness annotations "
                + "(JetBrains, JSR-305, Spring, FindBugs, Checker Framework, etc.) to their "
                + "JSpecify counterparts. Run FixTypeUseAnnotationPlacement first when you "
                + "need to inventory ambiguous declaration-vs-type-use placements.";
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
