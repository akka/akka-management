/*
 * Copyright (C) 2017 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.discovery.awsapi.ec2

import akka.discovery.awsapi.ec2.Ec2TagBasedSimpleServiceDiscovery.parseFiltersString
import com.amazonaws.services.ec2.model.Filter
import org.scalatest.FunSuite

class FiltersParsingTest extends FunSuite {

  import scala.collection.JavaConverters._

  test("empty string does not break parsing") {
    val filters = ""
    val result = parseFiltersString(filters)
    assert(result.isEmpty)
  }

  test("can parse simple filter") {
    val filters = "tag:purpose=demo"
    val result: List[Filter] = parseFiltersString(filters)
    assert(result.size == 1)
    assert(result.head.getName == "tag:purpose")
    assert(result.head.getValues.asScala.size == 1)
    assert(result.head.getValues.asScala.head == "demo")
  }

  test("can parse more complicated filter") {
    val filters = "tag:purpose=production;tag:department=engineering;tag:critical=no"
    val result = parseFiltersString(filters)
    assert(result.size == 3)
    assert(result.head.getName == "tag:purpose" && result.head.getValues.asScala == List("production"))
    assert(result(1).getName == "tag:department" && result(1).getValues.asScala == List("engineering"))
    assert(result(2).getName == "tag:critical" && result(2).getValues.asScala == List("no"))
  }

}
