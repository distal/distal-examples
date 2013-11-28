import ch.epfl.lsr.distal._

import java.util.concurrent.TimeUnit._
import ch.epfl.lsr.netty.network.ProtocolLocation

case class MSG(i :Int, j:Int) extends Message


class TestProtocol extends DSLProtocol {
  val ID = "TEST"
  override val LOCATION = new ProtocolLocation("lsr://TestProtocol@localhost:8080/TEST")

  var bound = 0
  var x = 0L

  UPON RECEIVING STATECHANGED DO {
    m =>
      println("STATECHANGED (bound="+bound+")")
  }

  UPON RECEIVING MSG WITH { _.i < bound+4} WITH { _.i > bound } TIMES 3 DO {
    ms =>
      println("A "+bound)
      //println(__runtime.messages.keys)
      bound = bound + 1
    | TRIGGER STATECHANGED()
  }

  UPON RECEIVING MSG WITH { m => println("check "+m); m.i == bound }  DO {
    m =>
      println("B "+bound)
      //println(__runtime.messages.keys)
    //bound = bound + 1
    | TRIGGER STATECHANGED()
    println("discard "+m)
    | DISCARD m
  }


  UPON RECEIVING START DO {
    s =>
    | AFTER 1(SECONDS) DO {

      x = System.currentTimeMillis
      | AFTER 3(MILLISECONDS) DO {
	println("3:"+(System.currentTimeMillis-x))
      }
      | AFTER 5(MILLISECONDS) DO {
	println("5:"+(System.currentTimeMillis-x))
      }
    | AFTER 10(MILLISECONDS) DO {
      println("10:"+(System.currentTimeMillis-x))
      }
    }
    | AFTER 2(SECONDS) DO {
      (1 to 10).foreach {
	i =>
	  | TRIGGER MSG(11-i, 1)
      }
    }
  }



}

object TestMain {

  def main(args :Array[String]) = {
    new TestProtocol().start

    Thread sleep 5000
    println("done " + new java.util.Date)
    System exit 0
  }

}
