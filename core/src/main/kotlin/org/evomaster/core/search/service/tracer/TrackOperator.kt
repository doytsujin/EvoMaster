package org.evomaster.core.search.service.tracer

/**
 * in order to refine the trace element, and further put the refined in its tracked list
 * the operator must implement [TrackOperator]
 */
interface TrackOperator{

    fun operatorTag() : String = this::class.java.simpleName
}
