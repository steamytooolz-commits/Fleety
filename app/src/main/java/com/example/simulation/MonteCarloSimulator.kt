package com.example.simulation

import com.example.model.CityZone
import com.example.model.VehicleClass
import kotlin.random.Random

data class MonteCarloInput(
    val currentCash: Double,
    val targetZone: CityZone,
    val additionalVehicles: Map<VehicleClass, Int>,
    val additionalDrivers: Int,
    val marketingSpend: Double,
    val currentDailyFixedCosts: Double = 350.0
)

data class MonteCarloDayResult(
    val day: Int,
    val p10Cash: Double,
    val p50Cash: Double,
    val p90Cash: Double
)

data class MonteCarloOutput(
    val dailyProjections: List<MonteCarloDayResult>,
    val p10FinalCash: Double,
    val p50FinalCash: Double,
    val p90FinalCash: Double,
    val breakEvenDay: Int?,
    val probabilityOfInsolvency: Double,
    val projectedNetRoi: Double
)

object MonteCarloSimulator {
    const val NUM_ITERATIONS = 500
    const val SIMULATION_DAYS = 30

    /**
     * Runs 500 Monte Carlo runs without mutating any real simulation state.
     */
    fun runSimulation(input: MonteCarloInput): MonteCarloOutput {
        // Initial capital outlay
        var initialCost = input.targetZone.unlockCost + input.marketingSpend
        for ((vClass, count) in input.additionalVehicles) {
            initialCost += vClass.purchasePrice * count
        }
        initialCost += input.additionalDrivers * 350.0 // recruitment cost

        val startingCashAfterExpansion = input.currentCash - initialCost

        // Matrix of daily cash per iteration: iterations x days
        val dailyCash = Array(NUM_ITERATIONS) { DoubleArray(SIMULATION_DAYS + 1) }
        var insolventRuns = 0
        val breakEvenDays = mutableListOf<Int>()

        val totalVehicleCount = input.additionalVehicles.values.sum().coerceAtLeast(1)
        val dailyVehicleUpkeep = input.additionalVehicles.entries.sumOf { it.key.dailyUpkeep * it.value }
        val dailyDriverWages = input.additionalDrivers * 120.0
        val baseDemand = (12.0 * input.targetZone.demandMultiplier * (1.0 + input.marketingSpend / 5000.0)).coerceAtLeast(4.0)

        for (iter in 0 until NUM_ITERATIONS) {
            val random = Random(1337 + iter * 31)
            var currentCash = startingCashAfterExpansion
            dailyCash[iter][0] = currentCash
            var reachedBreakEven = false

            for (day in 1..SIMULATION_DAYS) {
                if (currentCash <= 0.0) {
                    currentCash = 0.0
                    dailyCash[iter][day] = 0.0
                    continue
                }

                // Poisson-like variation in daily completed jobs (triangular/normal distribution approximation)
                val demandFactor = (random.nextDouble() * 0.5) + (random.nextDouble() * 0.5) + 0.5 // centered around 1.0
                val dailyJobsCompleted = ((baseDemand * demandFactor).toInt()).coerceAtMost(totalVehicleCount * 8)
                val avgRevenuePerJob = 160.0 + random.nextDouble() * 80.0
                val dailyRevenue = dailyJobsCompleted * avgRevenuePerJob

                // Operating costs: fuel + maintenance wear + incidents
                val dailyFuelCost = dailyJobsCompleted * (22.0 + random.nextDouble() * 10.0)
                val incidentRoll = random.nextDouble()
                val incidentCost = when {
                    incidentRoll < 0.08 -> 450.0 // Breakdown / tow
                    incidentRoll < 0.20 -> 120.0 // Low fuel / minor issue
                    else -> 0.0
                }

                val totalDailyExpenses = input.currentDailyFixedCosts +
                        dailyVehicleUpkeep +
                        dailyDriverWages +
                        dailyFuelCost +
                        incidentCost

                currentCash += (dailyRevenue - totalDailyExpenses)
                dailyCash[iter][day] = currentCash

                if (!reachedBreakEven && currentCash >= input.currentCash) {
                    breakEvenDays.add(day)
                    reachedBreakEven = true
                }
            }

            if (currentCash <= 0.0) {
                insolventRuns++
            }
        }

        // Aggregate percentiles for each day
        val dayResults = mutableListOf<MonteCarloDayResult>()
        for (day in 0..SIMULATION_DAYS) {
            val dayValues = DoubleArray(NUM_ITERATIONS) { iter -> dailyCash[iter][day] }.apply { sort() }
            val p10 = dayValues[(NUM_ITERATIONS * 0.10).toInt()]
            val p50 = dayValues[(NUM_ITERATIONS * 0.50).toInt()]
            val p90 = dayValues[(NUM_ITERATIONS * 0.90).toInt()]
            dayResults.add(MonteCarloDayResult(day, p10, p50, p90))
        }

        val finalP10 = dayResults.last().p10Cash
        val finalP50 = dayResults.last().p50Cash
        val finalP90 = dayResults.last().p90Cash

        val medianBreakEven = if (breakEvenDays.isNotEmpty()) {
            breakEvenDays.sorted()[breakEvenDays.size / 2]
        } else null

        val insolvencyProb = insolventRuns.toDouble() / NUM_ITERATIONS
        val netRoi = if (initialCost > 0) ((finalP50 - startingCashAfterExpansion) / initialCost) * 100.0 else 0.0

        return MonteCarloOutput(
            dailyProjections = dayResults,
            p10FinalCash = finalP10,
            p50FinalCash = finalP50,
            p90FinalCash = finalP90,
            breakEvenDay = medianBreakEven,
            probabilityOfInsolvency = insolvencyProb,
            projectedNetRoi = netRoi
        )
    }
}
