package com.kindrida.mylibrary

fun wasLoadTimeLessThanHoursAgo(numHours: Long, loadTime : Long): Boolean {
    val dateDifference = System.currentTimeMillis() - loadTime
    val numMilliSecondsPerHour = 3600000
    return dateDifference < numHours * numMilliSecondsPerHour
}