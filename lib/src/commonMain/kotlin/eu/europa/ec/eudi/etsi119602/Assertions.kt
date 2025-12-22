package eu.europa.ec.eudi.etsi119602

private fun cannotBeBlankMsg(field: String) = "$field cannot be blank"

internal fun requireNotBlank(
    v: String,
    field: String,
){
    require( v.isNotBlank()) { cannotBeBlankMsg(field) }
}

internal fun requireNullOrNotBlank(
    v: String?,
    field: String,
){
    if (v != null) requireNotBlank(v, field)
}


internal fun requireNullOrNonEmpty(list: List<*>?, name: String) {
    if (null != list) require(list.isNotEmpty()) { "$name cannot be empty" }
}

internal fun requireNonEmpty(list: List<*>, name: String) {
    require(list.isNotEmpty()) { "$name cannot be empty" }
}
