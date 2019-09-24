package org.evomaster.core.search.gene

import org.apache.commons.lang3.StringEscapeUtils
import org.evomaster.client.java.instrumentation.shared.StringSpecialization.*
import org.evomaster.client.java.instrumentation.shared.StringSpecializationInfo
import org.evomaster.client.java.instrumentation.shared.TaintInputName
import org.evomaster.core.output.OutputFormat
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.gene.GeneUtils.getDelta
import org.evomaster.core.search.impact.*
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.geneMutation.ArchiveMutator
import org.evomaster.core.search.service.mutator.geneMutation.IntMutationUpdate


class StringGene(
        name: String,
        var value: String = "foo",
        /** Inclusive */
        val minLength: Int = 0,
        /** Inclusive */
        val maxLength: Int = 16,
        /**
         * Depending on what a string is representing, there might be some chars
         * we do not want to use.
         * For example, in a URL Path variable, we do not want have "/", as otherwise
         * it would create 2 distinct paths
         */
        val invalidChars: List<Char> = listOf(),
        /**
         * Based on taint analysis, in some cases we can determine how some Strings are
         * used in the SUT.
         * For example, if a String is used as a Date, then it make sense to use a specialization
         * in which we mutate to have only Strings that are valid dates
         */
        var specializations: List<StringSpecializationInfo> = listOf(),

        val charsMutation : MutableList<IntMutationUpdate> = mutableListOf(),

        val lengthMutation : IntMutationUpdate = IntMutationUpdate(minLength, maxLength)
) : Gene(name) {

    companion object {
        /*
            WARNING
            mutable static state.
            only used to create unique names
         */
        private var counter: Int = 0
    }

    /*
        Even if through mutation we can get large string, we should
        avoid sampling very large strings by default
     */
    private val maxForRandomization = 16

    private var validChar: String? = null

    var specializationGene: Gene? = null

    /**
     * chars at 0..[mutatedIndex] (exclusion) are set, only used by archive-based mutation
     * when [mutatedIndex] = -1, it means that chars of [this] have not be mutated yet
     */
    var mutatedIndex : Int = -1


    override fun copy(): Gene {
        return StringGene(name, value, minLength, maxLength, invalidChars, specializations, charsMutation.map { it.copy() }.toMutableList(), lengthMutation.copy())
                .also {
                    it.specializationGene = this.specializationGene?.copy()
                    it.validChar = this.validChar
                    it.mutatedIndex = mutatedIndex
                }
    }


    override fun randomize(randomness: Randomness, forceNewValue: Boolean, allGenes: List<Gene>) {
        value = randomness.nextWordString(minLength, Math.min(maxLength, maxForRandomization))
        repair()
        specializationGene = null
    }

    override fun standardMutation(randomness: Randomness, apc: AdaptiveParameterControl, allGenes: List<Gene>) {

        if (specializationGene == null && specializations.isNotEmpty()) {
            chooseSpecialization()
            assert(specializationGene != null)
        }

        if (specializationGene != null) {
            specializationGene!!.standardMutation(randomness, apc, allGenes)
            return
        }

        if (specializationGene == null
                && !TaintInputName.isTaintInput(value)
                && randomness.nextBoolean(apc.getBaseTaintAnalysisProbability())) {

            value = TaintInputName.getTaintName(counter++.toString())
            return
        }

        val p = randomness.nextDouble()
        val s = value

        /*
            What type of mutations we do on Strings is strongly
            correlated on how we define the fitness functions.
            When dealing with equality, as we do left alignment,
            then it makes sense to prefer insertion/deletion at the
            end of the strings, and reward more "change" over delete/add
         */

        val others = allGenes.flatMap { g -> g.flatView() }
                .filterIsInstance<StringGene>()
                .map { g -> g.value }
                .filter { it != value }

        value = when {
            //seeding: replace
            p < 0.02 && !others.isEmpty() -> {
                randomness.choose(others)
            }
            //change
            p < 0.8 && s.isNotEmpty() -> {
                val delta = getDelta(randomness, apc, start = 6, end = 3)
                val sign = randomness.choose(listOf(-1, +1))
                val i = randomness.nextInt(s.length)
                val array = s.toCharArray()
                array[i] = s[i] + (sign * delta)
                String(array)
            }
            //delete last
            p < 0.9 && s.isNotEmpty() && s.length > minLength -> {
                s.dropLast(1)
            }
            //append new
            s.length < maxLength -> {
                if (s.isEmpty() || randomness.nextBoolean(0.8)) {
                    s + randomness.nextWordChar()
                } else {
                    val i = randomness.nextInt(s.length)
                    if (i == 0) {
                        randomness.nextWordChar() + s
                    } else {
                        s.substring(0, i) + randomness.nextWordChar() + s.substring(i, s.length)
                    }
                }
            }
            else -> {
                //do nothing
                s
            }
        }

        repair()
    }

    private fun chooseSpecialization() {
        assert(specializations.isNotEmpty())

        specializationGene = when {
            specializations.any { it.stringSpecialization == DATE_YYYY_MM_DD } -> DateGene(name)

            specializations.any { it.stringSpecialization == INTEGER } -> IntegerGene(name)

            specializations.any { it.stringSpecialization == CONSTANT } -> EnumGene<String>(name,
                        specializations.filter { it.stringSpecialization == CONSTANT }.map { it.value }
                )

            else -> {
                //should never happen
                throw IllegalStateException("Cannot handle specialization")
            }
        }
    }

    /**
     * Make sure no invalid chars is used
     */
    fun repair() {
        if (invalidChars.isEmpty()) {
            //nothing to do
            return
        }

        if (validChar == null) {
            //compute a valid char
            for (c in 'a'..'z') {
                if (!invalidChars.contains(c)) {
                    validChar = c.toString()
                    break
                }
            }
        }
        if (validChar == null) {
            //no basic char is valid??? TODO should handle this situation, although likely never happens
            return
        }

        for (invalid in invalidChars) {
            value = value.replace("$invalid", validChar!!)
        }
    }

    override fun getValueAsPrintableString(previousGenes: List<Gene>, mode: String?, targetFormat: OutputFormat?): String {

        if (specializationGene != null) {
            return "\"" + specializationGene!!.getValueAsPrintableString(previousGenes, mode, targetFormat) + "\""
        }

        val rawValue = getValueAsRawString()
        if (mode != null && mode.equals("xml")) {
            return StringEscapeUtils.escapeXml(rawValue)
        } else {
            when {
                // TODO this code should be refactored with other getValueAsPrintableString() methods
                (targetFormat == null) -> return "\"$rawValue\""
                targetFormat.isKotlin() -> return "\"$rawValue\""
                        .replace("\\", "\\\\")
                        .replace("$", "\\$")
                else -> return "\"$rawValue\""
                        .replace("\\", "\\\\")
            }
        }
    }

    override fun getValueAsRawString(): String {
        if (specializationGene != null) {
            return specializationGene!!.getValueAsRawString()
        }
        return value
    }

    override fun copyValueFrom(other: Gene) {
        if (other !is StringGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        this.value = other.value
        if (other.specializationGene == null) {
            this.specializationGene = null
        } else {
            this.specializationGene?.copyValueFrom(other.specializationGene!!)
        }
    }

    override fun containsSameValueAs(other: Gene): Boolean {
        if (other !is StringGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }

        if ((this.specializationGene == null && other.specializationGene != null) ||
                (this.specializationGene != null && other.specializationGene == null)) {
            return false
        }

        if (this.specializationGene != null) {
            return this.specializationGene!!.containsSameValueAs(other.specializationGene!!)
        }

        return this.value == other.value
    }

    override fun archiveMutation(
            randomness: Randomness,
            allGenes: List<Gene>,
            apc: AdaptiveParameterControl,
            selection: ImpactMutationSelection,
            geneImpact: GeneImpact?,
            geneReference : String,
            archiveMutator: ArchiveMutator,
            evi: EvaluatedIndividual<*>
    ) {
        if (specializationGene == null && specializations.isNotEmpty()) {
            chooseSpecialization()
            assert(specializationGene != null)
        }

        if (specializationGene != null) {
            val impact = geneImpact?: evi.getImpactOfGenes()[ImpactUtils.generateGeneId(evi.individual, this)] ?: throw IllegalStateException("cannot find this gene in the individual")
            specializationGene!!.archiveMutation(randomness, allGenes, apc, selection, impact, geneReference, archiveMutator, evi)
            return
        }

        archiveMutator.mutate(this)
    }

    override fun reachOptimal() : Boolean{
       return lengthMutation.reached && (charsMutation.all { it.reached  }  || charsMutation.isEmpty())
    }

    override fun archiveMutationUpdate(original: Gene, mutated: Gene, doesCurrentBetter: Boolean, archiveMutator: ArchiveMutator) {
        original as? StringGene ?: throw IllegalStateException("$original should be StringGene")
        mutated as? StringGene ?: throw IllegalStateException("$mutated should be StringGene")

        val previous = original.value
        val current = mutated.value

        if (previous.length != current.length){
            if (this != mutated){
                this.lengthMutation.reached = mutated.lengthMutation.reached
            }
            lengthUpdate(previous, current, mutated, doesCurrentBetter, archiveMutator)
        }else{
            if (mutatedIndex == -1){
                initCharMutation()
            }
            if (this != mutated)
                mutatedIndex = mutated.mutatedIndex

            charUpdate(previous, current, mutated, doesCurrentBetter, archiveMutator)
        }
    }
    private fun charUpdate(previous:String, current: String, mutated: StringGene, doesCurrentBetter: Boolean, archiveMutator: ArchiveMutator) {
        val charUpdate = if (archiveMutator.relaxIndexStringGeneMutation()) charsMutation[mutatedIndex] else charsMutation.first()
        if (this != mutated){
            charUpdate.reached = (if (archiveMutator.relaxIndexStringGeneMutation()) mutated.charsMutation[mutatedIndex] else mutated.charsMutation.first()).reached
        }

        val pchar = previous[mutatedIndex].toInt()
        val cchar = current[mutatedIndex].toInt()

        /*
            1) current char is not in min..max, but current is better -> reset
            2) cmutation is optimal, but current is better -> reset
         */
        val reset = doesCurrentBetter && (
                cchar !in charUpdate.preferMin..charUpdate.preferMax ||
                        charUpdate.reached
                )

        if (reset){
            charUpdate.preferMax = Char.MAX_VALUE.toInt()
            charUpdate.preferMin = Char.MIN_VALUE.toInt()
            charUpdate.reached = false
            return
        }
        charUpdate.updateBoundary(pchar, cchar,doesCurrentBetter)

        val exclude = value[mutatedIndex].toInt()

        if (!archiveMutator.checkIfHasCandidates(charUpdate.preferMin, charUpdate.preferMax, exclude = invalidChars.map { it.toInt() }.plus(exclude))){
            charUpdate.reached = true
            if (!archiveMutator.relaxIndexStringGeneMutation()){
                mutatedIndex += 1
                charUpdate.counter = 0
                archiveMutator.resetCharMutationUpdate(charUpdate)
            }
        }
    }

    private fun lengthUpdate(previous:String, current: String, mutated: Gene, doesCurrentBetter: Boolean, archiveMutator: ArchiveMutator) {
        //update charsMutation regarding value
        val added = value.length - charsMutation.size
        if (added != 0){
            if (added > 0){
                (0 until added).forEach { _->
                    charsMutation.add(IntMutationUpdate(Char.MIN_VALUE.toInt(), Char.MAX_VALUE.toInt()))
                }
            }else{
                (0 until -added).forEach {
                    charsMutation.removeAt(charsMutation.size - 1)
                }
            }
        }
        /*
            1) current.length is not in min..max, but current is better -> reset
            2) lengthMutation is optimal, but current is better -> reset
         */
        val reset = doesCurrentBetter && (
                current.length !in lengthMutation.preferMin..lengthMutation.preferMax ||
                        lengthMutation.reached
                )

        if (reset){
            lengthMutation.preferMin = minLength
            lengthMutation.preferMax = maxLength
            lengthMutation.reached = false
            return
        }

        lengthMutation.updateBoundary(previous.length, current.length, doesCurrentBetter)

        if (lengthMutation.preferMin == lengthMutation.preferMax){
            lengthMutation.reached = true
            if (value.isEmpty()){
                if (!archiveMutator.relaxIndexStringGeneMutation()){
                    charsMutation.first().reached = true
                    mutatedIndex = 0
                }
            }
        }
    }

    private fun initCharMutation(){
        charsMutation.clear()
        charsMutation.addAll((0 until value.length).map { IntMutationUpdate(Char.MIN_VALUE.toInt(), Char.MAX_VALUE.toInt()) })
    }
}