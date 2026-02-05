package eu.europa.ec.eudi.etsi1196x2.consultation

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = SENSITIVE_API_MESSAGE,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class SensitiveApi

private const val SENSITIVE_API_MESSAGE =
    "This API is sensitive and can potentially bypass security checks if misconfigured."