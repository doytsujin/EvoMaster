package org.evomaster.core.search.gene

import org.evomaster.core.search.service.Randomness

/**
 * A gene specifically designed to handle Foreign Keys in SQL databases.
 * This is tricky, as an Insertion operation to create a new row in a database
 * would have to depend on previous insertions when dealing with foreign keys.
 * Changing the primary key of a previous insertion would require updating the
 * foreign key as well.
 * There is no point whatsoever in trying (and fail) to add invalid SQL data.
 *
 * To complicate things even more, the value of a foreign key might not be even
 * known beforehand, as primary keys could be dynamically generated by the database.
 */
class SqlForeignKeyGene(
        sourceColumn: String,
        val uniqueId: Long,
        /**
         * The name of the table this FK points to
         */
        val targetTable: String,
        val nullable: Boolean,
        /**
         * A negative value means this FK is not bound yet.
         * Otherwise, it should be equal to the uniqueId of
         * a previous SqlPrimaryKey
         */
        var uniqueIdOfPrimaryKey: Long = -1

) : Gene(sourceColumn) {

    init {
        if(uniqueId < 0){
            throw IllegalArgumentException("Negative unique id")
        }
    }

    override fun copy() = SqlForeignKeyGene(name, uniqueId, targetTable, nullable, uniqueIdOfPrimaryKey)

    override fun randomize(randomness: Randomness, forceNewValue: Boolean) {
        throw IllegalStateException("Cannot randomize a foreign key without knowing the state of other SQL inserted data")
    }

    fun randomize(randomness: Randomness, forceNewValue: Boolean, allGenes: List<Gene>) {

        //All the ids of previous PKs for the target table
        val pks = allGenes.asSequence()
                .takeWhile { it !is SqlForeignKeyGene || it.uniqueId != uniqueId }
                .filterIsInstance(SqlPrimaryKeyGene::class.java)
                .filter { it.tableName == targetTable }
                .map { it.uniqueId }
                .toSet()

        if (pks.isEmpty()) {
            if (!nullable) {
                throw IllegalStateException("Trying to bind a non-nullable FK, but not valid PK is found")
            } else {
                uniqueIdOfPrimaryKey = -1
                return
            }
        }

        /*
            If cannot be NULL, then have to choose from existing PKs
         */
        if (!nullable) {
            uniqueIdOfPrimaryKey = if (pks.size == 1) {
                //only one possible option
                pks.first()
            } else {
                if(! forceNewValue){
                    randomness.choose(pks)
                } else {
                    randomness.choose(pks.filter { it != uniqueIdOfPrimaryKey })
                }
            }
            return
        }

        /*
            If it can be NULL, we have the option of NULL plus the PKs
         */
        uniqueIdOfPrimaryKey = if(!isBound()){
            //not bound, ie NULL? choose from PKs
            randomness.choose(pks)
        } else if (randomness.nextBoolean(0.1)) {
            //currently bound, but with certain probability we set it to NULL
             -1
        } else {
            if(! forceNewValue || pks.size == 1){
                randomness.choose(pks)
            } else {
                randomness.choose(pks.filter { it != uniqueIdOfPrimaryKey })
            }
        }

    }

    override fun getValueAsPrintableString(): String {
        throw IllegalStateException("This method should never be called. Use version that takes as input genes instead")
    }

    override fun getValueAsPrintableString(previousGenes: List<Gene>): String {

        if (!isBound()) {
            if (!nullable) {
                throw IllegalStateException("Foreign key '$name' for table $targetTable is not bound")
            } else {
                return "null"
            }
        }

        val pk = previousGenes.find { it is SqlPrimaryKeyGene && it.uniqueId == uniqueIdOfPrimaryKey }
                ?: throw IllegalArgumentException("Input genes do not contain primary key with id $uniqueIdOfPrimaryKey")

        if(! pk.isPrintable()){
            //this can happen if the PK is autoincrement
            throw IllegalArgumentException("Trying to print a Foreign Key pointing to a non-printable Primary Key")
        }

        return pk.getValueAsPrintableString()
    }

    fun isReferenceToNonPrintable(previousGenes: List<Gene>) : Boolean{
        if(! isBound()){
            return false
        }

        val pk = previousGenes.find { it is SqlPrimaryKeyGene && it.uniqueId == uniqueIdOfPrimaryKey }
                ?: throw IllegalArgumentException("Input genes do not contain primary key with id $uniqueIdOfPrimaryKey")

        return ! pk.isPrintable()
    }

    private fun isBound() = uniqueIdOfPrimaryKey >= 0

    override fun copyValueFrom(other: Gene) {
        if (other !is SqlForeignKeyGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        this.uniqueIdOfPrimaryKey = other.uniqueIdOfPrimaryKey
    }

}