package com.github.scaladdi

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.{Actor, ActorRef}
import shapeless.ops.hlist.Prepend
import shapeless.{|¬|, HNil, HList, ::}

import scala.concurrent.{ExecutionContext, Future}

// czy nieuzywane typy mozna zastapic przez _ ?
class ActorDependencies[Futures <: HList, Values <: HList, DepFutures <: HList, DepValues <: HList, AD <: HList, FAD <: HList, Responses <: HList, Failures <: HList](
  val futureDeps: FutureDependencies[Futures, Values, DepFutures, DepValues], val actorDeps: FAD)
  (implicit isActorDeps: IsActorDeps[AD, Responses, Failures],
    joinResults: Prepend[DepValues, Responses],
    futureActorDeps: IsHListOfFutures[FAD, AD],
    ec: ExecutionContext) {

  import AlignReduceOps._

  def and[Q <: HList, R, F, FQ <: HList](dep: ActorDep[Q, R, F])
    (implicit notInResponses: NotIn[R, Responses],
      notInFailures: NotIn[F, Failures],
      futured: IsHListOfFutures[FQ, Q],
      align: AlignReduce[Futures, FQ],
      prependNewResult: Prepend[DepValues, R :: Responses]) = {
    new ActorDependencies[Futures, Values, DepFutures, DepValues, ActorDepAction[R, F] :: AD,
      Future[ActorDepAction[R, F]] :: FAD, R :: Responses, F :: Failures](
      futureDeps,
      futured.hsequence(futureDeps.dependencies.alignReduce[FQ])
        .map(x => ActorDepAction[R, F](dep.who, dep.question(x))) :: actorDeps
    )
  }
}

case class ActorDep[Question <: HList, R, F](who: ActorRef, question: Question => Any) {
  type Response = R
  type Failure = F
}

case class ActorDepAction[R, F](who: ActorRef, question: Any) {
  type Response = R
  type Failure = F
}

trait IsActorDeps[L <: HList, R <: HList, F <: HList] {
  type Responses = R
  type Failures = F
}

object IsActorDeps {
  implicit val forHNil: IsActorDeps[HNil, HNil, HNil] = new IsActorDeps[HNil, HNil, HNil] {}

  /*implicit def is[T <: HList, Resps <: HList, Fails <: HList, R, F](l: ActorDepAction[R, F] :: T)(implicit isActorDeps: IsActorDeps[T, Resps, Fails]):
  IsActorDeps[ActorDepAction[R, F] :: T, R :: Resps, F :: Fails] =
    new IsActorDeps[ActorDepAction[R, F] :: T, R :: Resps, F :: Fails] {}*/

  implicit def is[T <: HList, Resps <: HList, Fails <: HList, R, F](implicit isActorDeps: IsActorDeps[T, Resps, Fails]):
  IsActorDeps[ActorDepAction[R, F] :: T, R :: Resps, F :: Fails] =
    new IsActorDeps[ActorDepAction[R, F] :: T, R :: Resps, F :: Fails] {}
}

trait NotIn[T, L <: HList]

object NotIn {
  implicit def notInHNil[T] = new NotIn[T, HNil] {}

  implicit def notInList[H, T <: |¬|[H], L <: HList](implicit notInTail: NotIn[T, L]) = new NotIn[T, H :: L] {} // lambda
}




//ActorDependencies[Futures, Values, DepFutures, DepValues, ActorDepAction[dep.Response, dep.Failure] :: AD,
//  Future[ActorDepAction[dep.Response, dep.Failure]] :: FAD, dep.Response :: Responses, dep.Failure :: Failures]