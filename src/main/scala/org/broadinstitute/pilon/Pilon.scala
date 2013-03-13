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

import java.io.File

object Pilon {
  // types of fixing we know about
  val fixChoices = List('bases, 'gaps, 'local)
  val experimentalFixChoices = List('novel, 'breaks)

  // input parameters
  var bamFiles = List[BamFile]()
  var targets = ""
  var genomePath = ""
  // parameters governing output
  var prefix = "pilon"
  var tracks = false
  var verbose = false
  var vcf = false
  var debug = false
  // heuristics and control parameters
  var diploid = false
  var fixList = fixChoices
  var flank = 10
  var gapMargin = 1000
  var minMinDepth = 5
  var minGap = 10
  var minDepth = 0.1
  var minQual = 0
  var pf = false
  var strays = true
  
  // for logging to output files
  var commandArgs = Array[String]()
  
  def main(args: Array[String]) {
    commandArgs = args
    println(Version.version)
    optionParse(args.toList)

    // Stray computation is expensive up front, so only turn it on
    // if we're doing local reassembly
    strays &= (fixList contains 'gaps) || (fixList contains 'local)
    
    if (bamFiles.length == 0) {
      println(usage)
      sys.exit(0)
    } else if (genomePath == "") {
      println("Must specify a --genome and one or more bam files (--frags, --jumps, or --unpaired)\n\n" + usage)
      sys.exit(1)
    }

    println("Genome: " + genomePath)
    val genome = new GenomeFile(new File(genomePath), targets)

    genome.processRegions(bamFiles)

    if (tracks) { 
      val tracks = new Tracks(genome, prefix)
      tracks.standardTracks
    }
    
  }

  def optionParse(list: List[String]) : Unit = {
    def isSwitch(s: String) = (s(0) == '-')
    list match {
      case Nil => Nil
      case "--help" :: tail =>
        println(usage)
        print(help)
        sys.exit(0)
      case "--debug" :: tail =>
        debug = true
        verbose = true
        optionParse(tail)
      case "--diploid" :: tail =>
        diploid = true
        optionParse(tail)
      case "--fix" :: value :: tail =>
        fixList = parseFixList(value)
        optionParse(tail)
      case "--flank" :: value :: tail =>
        flank = value.toInt
        optionParse(tail)
      case "--frags" :: value :: tail =>
        bamFiles ::= new BamFile(new File(value), 'frags)
        optionParse(tail)
      case "--gapmargin" :: value :: tail =>
        gapMargin = value.toInt
        optionParse(tail)
      case "--genome" :: value :: tail =>
        genomePath = value
        optionParse(tail)
      case "--jumps" :: value :: tail =>
        bamFiles ::= new BamFile(new File(value), 'jumps)
        optionParse(tail)
      case "--mindepth" :: value :: tail =>
        minDepth = value.toDouble
        optionParse(tail)
      case "--mingap" :: value :: tail =>
        minGap = value.toInt
        optionParse(tail)
      case "--minqual" :: value :: tail =>
        minQual = value.toInt
        optionParse(tail)
      case "--output" :: value :: tail =>
        prefix = value
        optionParse(tail)
      case "--pf" :: tail =>
        pf = true
        optionParse(tail)
      case "--targets" :: value :: tail =>
        targets = value
        optionParse(tail)
      case "--tracks" :: tail =>
        tracks = true
        optionParse(tail)
      case "--unpaired" :: value :: tail =>
        bamFiles ::= new BamFile(new File(value), 'unpaired)
        optionParse(tail)
      case "--vcf" :: tail =>
        vcf = true
        optionParse(tail)
      case "--verbose" :: tail =>
        verbose = true
        optionParse(tail)
      case "--nostrays" :: tail =>
        strays = false
        optionParse(tail)
      case option :: tail =>
        println("Unknown option " + option)
        sys.exit(1)
    }
  }
  
  def parseFixList(fix: String) = {
    val fixes = fix.split(",")
    var fixList = List[Symbol]()
    for (f <- fixes) {
      val fsym = Symbol(f)
      if (fsym == 'all) fixList = fixChoices
      else if (fsym == 'none) fixList = List.empty
      else if (fixChoices contains fsym) fixList ::= fsym
      else if (experimentalFixChoices contains fsym) {
        println("Warning: experimental fix option " + f)
        fixList ::= fsym 
      }
      else {
        println("Error: unknown fix option " + f)
        sys.exit(1)
      }
    }
    fixList
  }
  
  def outputFile(name : String) = new File(prefix + name)
  
  val usage = """
    Usage: pilon --genome genome.fasta [--frags frags.bam] [--jumps jumps.bam] [--unpaired unpaired.bam]
                 [...other options...]
           pilon --help for option details 
"""

  val help = """
         INPUTS:
           --genome genome.fasta
              The input genome we are trying to improve, which must be the reference used
              for the bam alignments.  At least one of --frags or --jumps must also be given.
           --frags frags.bam
              A bam file consisting of fragment paired-end alignments, aligned to the --genome
              argument using bwa or bowtie2.  This argument may be specifed more than once.
           --jumps jumps.bam
              A bam file consisting of jump (mate pair) paired-end alignments, aligned to the
              --genome argument using bwa or bowtie2.  This argument may be specifed more than once.
           --unpaired unpaired.bam
              A bam file consisting of unpaired alignments, aligned to the --genome argument 
              using bwa or bowtie2.  This argument may be specifed more than once.
         OUTPUTS:
           --output
              Prefix for output files
           --vcf
              If specified, a vcf file will be generated
           --tracks
              This options will cause many track files (*.bed, *.wig) suitable for viewing in
              IGV to be written.
         CONTROL:
           --diploid
              Sample is from diploid organism; will eventually affect calling of heterozygous SNPs
           --fix fixlist
              A comma-separated list of categories of issues to try to fix: 
              "bases": try to fix individual bases and small indels; 
              "gaps": try to fill gaps; 
              "local": try to detect and fix local misassemblies;
              "all": all of the above (default);
              "none": none of the above; new fasta file will not be written.
    		  The following are experimental fix types:
              "breaks": allow local reassembly to open new gaps (with "local").
              "novel": assemble novel sequence from unaligned non-jump reads.
           --pf
              Only include reads which pass quality filtering by sequencing instrument.
           --targets targetlist
              Only process the specified target(s).  Targets are comma-separated, and each target
              is a fasta element name optionally followed by a base range.  
              Example: "scaffold00001,scaffold00002:10000-20000" would result in processing all of
              scaffold00001 and coordinates 10000-20000 of scaffold00002.
           --verbose
              More verbose output.
           --debug
              Debugging output (implies verbose).
         HEURISTICS:
           --flank nbases
              Controls how much of the well-aligned reads will be used; this many bases at each
              end of the good reads will be ignored (default 10).
           --gapmargin
              Closed gaps must be within this number of bases of true size to be closed (1000)
           --mindepth depth
              Variants (snps and indels) will only be called if there is coverage of good pairs
              at this depth or more; if this value is >= 1, it is an absolute depth, if it is a
              fraction < 1, then minimum depth is computed by multiplying this value by the mean
              coverage for the region, with a minumum value of 5 (default 0.1: min depth to call 
              is 10% of mean coverage or 5, whichever is greater).
           --mingap
              Minimum size for unclosed gaps (default 10)
           --minqual
              Minimum base quality to consider for pileups (default 0)
  """  
}
