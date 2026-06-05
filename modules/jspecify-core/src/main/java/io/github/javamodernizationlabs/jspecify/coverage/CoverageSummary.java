package io.github.javamodernizationlabs.jspecify.coverage;

/**
 * Aggregate counts describing JSpecify nullness coverage of a project's public
 * API.
 *
 * <p>The raw counts are paired with ratio accessors that guard against division
 * by zero by returning {@code 0.0} when the corresponding denominator is zero.
 *
 * @param publicApiElements total number of public API elements (types, methods
 *     and fields) seen
 * @param specifiedPublicApiElements number of those elements with an explicit
 *     nullness contract
 * @param nullMarkedPackages number of packages declared {@code @NullMarked}
 * @param packagesSeen number of public-API packages encountered
 * @param ambiguousAnnotations number of annotations whose nullness intent could
 *     not be determined unambiguously
 * @param publicMethods total number of public methods seen
 * @param returnNullnessSpecified number of public methods whose return nullness
 *     is specified
 * @param publicParameters total number of public method parameters seen
 * @param parameterNullnessSpecified number of parameters whose nullness is
 *     specified
 * @param genericTypeUses total number of generic type uses seen
 * @param genericTypeUseNullnessSpecified number of generic type uses whose
 *     nullness is specified
 * @param kotlinInteropWarnings number of potential Kotlin interop warnings
 *     implied by unspecified return nullness
 */
public record CoverageSummary(
        int publicApiElements,
        int specifiedPublicApiElements,
        int nullMarkedPackages,
        int packagesSeen,
        int ambiguousAnnotations,
        int publicMethods,
        int returnNullnessSpecified,
        int publicParameters,
        int parameterNullnessSpecified,
        int genericTypeUses,
        int genericTypeUseNullnessSpecified,
        int kotlinInteropWarnings
) {
    /**
     * Returns the fraction of public API elements with a specified nullness
     * contract.
     *
     * @return the ratio of specified to total public API elements, or
     *     {@code 0.0} if there are no public API elements
     */
    public double specifiedRatio() {
        return publicApiElements == 0
                ? 0.0d
                : (double) specifiedPublicApiElements / (double) publicApiElements;
    }

    /**
     * Returns the fraction of public-API packages that are {@code @NullMarked}.
     *
     * @return the ratio of {@code @NullMarked} packages to packages seen, or
     *     {@code 0.0} if no packages were seen
     */
    public double nullMarkedPackageRatio() {
        return packagesSeen == 0
                ? 0.0d
                : (double) nullMarkedPackages / (double) packagesSeen;
    }

    /**
     * Returns the fraction of public methods whose return nullness is
     * specified.
     *
     * @return the ratio of return-nullness-specified methods to public methods,
     *     or {@code 0.0} if there are no public methods
     */
    public double returnNullnessRatio() {
        return publicMethods == 0
                ? 0.0d
                : (double) returnNullnessSpecified / (double) publicMethods;
    }

    /**
     * Returns the fraction of public parameters whose nullness is specified.
     *
     * @return the ratio of nullness-specified parameters to public parameters,
     *     or {@code 0.0} if there are no public parameters
     */
    public double parameterNullnessRatio() {
        return publicParameters == 0
                ? 0.0d
                : (double) parameterNullnessSpecified / (double) publicParameters;
    }

    /**
     * Returns the fraction of generic type uses whose nullness is specified.
     *
     * @return the ratio of nullness-specified generic type uses to total
     *     generic type uses, or {@code 0.0} if there are none
     */
    public double genericTypeUseRatio() {
        return genericTypeUses == 0
                ? 0.0d
                : (double) genericTypeUseNullnessSpecified / (double) genericTypeUses;
    }
}
