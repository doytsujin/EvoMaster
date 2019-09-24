#! /bin/bash

cd target

case $1 in
	1)
	  echo "APPROACH_GOOD 0.5"
		java -jar evomaster.jar --stoppingCriterion FITNESS_EVALUATIONS --heuristicsForSQL false --generateSqlDataWithSearch false --enableTrackIndividual false --enableTrackEvaluatedIndividual true --probOfArchiveMutation 0.5 --geneSelectionMethod APPROACH_GOOD --maxActionEvaluations 100000
		;;
  2)
	  echo "AWAY_BAD 0.5"
		java -jar evomaster.jar --stoppingCriterion FITNESS_EVALUATIONS --heuristicsForSQL false --generateSqlDataWithSearch false --enableTrackIndividual false --enableTrackEvaluatedIndividual true --probOfArchiveMutation 0.5 --geneSelectionMethod AWAY_BAD --maxActionEvaluations 100000
		;;
  3)
	  echo "FEED_BACK 0.5"
		java -jar evomaster.jar --stoppingCriterion FITNESS_EVALUATIONS --heuristicsForSQL false --generateSqlDataWithSearch false --enableTrackIndividual false --enableTrackEvaluatedIndividual true --probOfArchiveMutation 0.5 --geneSelectionMethod FEED_BACK --maxActionEvaluations 100000
		;;
  *)
    exit 1
		;;
esac
