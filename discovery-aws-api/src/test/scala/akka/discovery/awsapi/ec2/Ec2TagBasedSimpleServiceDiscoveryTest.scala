/*
 * Copyright (C) 2017-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.discovery.awsapi.ec2

import akka.discovery.awsapi.ec2.Ec2TagBasedServiceDiscovery.parseFiltersString
import com.amazonaws.services.ec2.model.Filter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class FiltersParsingTest extends AnyFunSuite with Matchers {

  import scala.collection.JavaConverters._

  test("empty string does not break parsing") {
    val filters = ""
    val result: List[Filter] = parseFiltersString(filters)
    result should be('empty)
  }

  test("can parse simple filter") {
    val filters = "tag:purpose=demo"
    val result: List[Filter] = parseFiltersString(filters)
    result should have size 1
    result.head.getName should ===("tag:purpose")
    result.head.getValues.asScala should have size 1
    result.head.getValues.asScala.head should ===("demo")
  }

  test("can parse more complicated filter") {
    val filters = "tag:purpose=production;tag:department=engineering;tag:critical=no"
    val result = parseFiltersString(filters)
    result should have size 3

    result.head.getName should ===("tag:purpose")
    result.head.getValues.asScala should ===(List("production"))

    result(1).getName should ===("tag:department")
    result(1).getValues.asScala should ===(List("engineering"))

    result(2).getName should ===("tag:critical")
    result(2).getValues.asScala should ===(List("no"))
  }

}
