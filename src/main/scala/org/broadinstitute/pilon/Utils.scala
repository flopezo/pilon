/*
 * Copyright (c) 2013. The Broad Institute, Inc.
 * SOFTWARE COPYRIGHT NOTICE
 * This software and its documentation are the copyright of the Broad Institute, Inc.
 * All rights are reserved.
 *
 * This software is supplied without any warranty or guaranteed support whatsoever.
 * The Broad Institute is not responsible for its use, misuse, or functionality.
 */

package org.broadinstitute.pilon

object Utils {
  def roundDiv(n: Long, d: Long) = if (d > 0) (n + d/2) / d else 0
  def roundDiv(n: Int, d: Int) = if (d > 0) (n + d/2) / d else 0
  def pct(n: Long, d: Long) = roundDiv(100 * n, d)
  def pct(n: Int, d: Int) = roundDiv(100 * n, d)
}