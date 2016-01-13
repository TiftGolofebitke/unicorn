/*******************************************************************************
 * (C) Copyright 2015 ADP, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package unicorn.unibase

import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import unicorn.bigtable.hbase.HBase
import unicorn.json._

/**
 * @author Haifeng Li
 */
class HBaseBucketSpec extends Specification with BeforeAfterAll {
  // Make sure running examples one by one.
  // Otherwise, test cases on same columns will fail due to concurrency
  sequential
  val bigtable = HBase()
  val db = new HUnibase(bigtable)
  val tableName = "unicorn_unibase_test"
  val json = JsonParser(
    """
      |{
      |  "owner": "Rich",
      |  "phone": "123-456-7890",
      |  "address": {
      |    "street": "1 ADP Blvd.",
      |    "city": "Roseland",
      |    "state": "NJ"
      |  },
      |  "money": 1L,
      |  "store": {
      |    "books": 10L,
      |    "book": [
      |      {
      |        "category": "reference",
      |        "author": "Nigel Rees",
      |        "title": "Sayings of the Century",
      |        "price": 8.95
      |      },
      |      {
      |        "category": "fiction",
      |        "author": "Evelyn Waugh",
      |        "title": "Sword of Honour",
      |        "price": 12.99
      |      },
      |      {
      |        "category": "fiction",
      |        "author": "Herman Melville",
      |        "title": "Moby Dick",
      |        "isbn": "0-553-21311-3",
      |        "price": 8.99
      |      },
      |      {
      |        "category": "fiction",
      |        "author": "J. R. R. Tolkien",
      |        "title": "The Lord of the Rings",
      |        "isbn": "0-395-19395-8",
      |        "price": 22.99
      |      }
      |    ],
      |    "bicycle": {
      |      "color": "red",
      |      "price": 19.95
      |    }
      |  }
      |}
    """.stripMargin).asInstanceOf[JsObject]

  override def beforeAll = {
    db.createBucket(tableName)
  }

  override def afterAll = {
    db.dropBucket(tableName)
  }

  "HBaseBucket" should {
    "insert" in {
      val bucket = db(tableName)
      val key = bucket.upsert(json)
      key === json("_id")
      println(json)
      val obj = bucket(key)
      obj.get === json

      bucket.insert(json) === false
      bucket.delete(key)
      bucket(key) === None
    }
    /*
    "inc" in {
      val bucket = db(tableName)
      val key = bucket.upsert(json)

      val update = JsonParser(
        """
          | {
          |   "$inc": {
          |     "money": 1,
          |     "store.books": 10
          |   }
          | }
        """.stripMargin).asInstanceOf[JsObject]
      update("_id") = key
      bucket.update(update)

      val doc = bucket(key).get
      doc.money === 2
      doc.store.books === 20

      // update again
      bucket.update(update)

      val doc2 = bucket(key).get
      doc2.money === 2
      doc2.store.books === 20
    }
    */
  }
}
