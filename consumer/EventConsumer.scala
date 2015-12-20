import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import kafka.serializer._
import com.softwaremill.react.kafka._

trait Model {
  case class Observation(time: Long, value: BigDecimal)
  object Zero extends Observation(0,0)

  case class Memory(length: Int, observation: Observation, pastEvents: List[Observation]) {

    lazy val pastValues = pastEvents.map(_.value)

    def record(newObservation: Observation): Memory = {
      val newEvents = (newObservation :: pastEvents).takeWhile(_.time > newObservation.time - length)
      new Memory(length, newObservation, newEvents)
    }

    override def toString = {
      s"${observation.time} ${observation.value} ${pastEvents.size} ${pastValues.sum} ${pastValues.min} ${pastValues.max}"
    }
  }

}

trait KafkaConfig {
  lazy val kafkaProperties = ConsumerProperties(
    brokerList = "localhost:9092",
    zooKeeperHost = "localhost:2181",
    topic = "events",
    groupId = "eventConsumers",
    decoder = new StringDecoder()
  )
}

object EventConsumer extends App with Model with KafkaConfig {
  implicit val actorSystem  = ActorSystem("EventConsumer")
  implicit val materializer = ActorMaterializer()
  import actorSystem.dispatcher
        
  val kafka = new ReactiveKafka()
  val topic = kafka.consume(kafkaProperties)

  val T = 60

  Source(topic)
    .map(_.message.split(' ')).map(parts => Observation(parts(0).toLong, BigDecimal(parts(1))))
    .scan(Memory(T, Zero, Nil))((m,o) => m.record(o))
    .drop(1)
    .to(Sink.foreach(println))
    .run()
       
}
