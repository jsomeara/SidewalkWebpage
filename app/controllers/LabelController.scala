package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator
import controllers.headers.ProvidesHeader
import formats.json.LabelFormats._
import models.label._
import models.user.User
import play.api.libs.json._
import play.api.mvc.Action

import scala.concurrent.Future


class LabelController @Inject() (implicit val env: Environment[User, SessionAuthenticator])
  extends Silhouette[User, SessionAuthenticator] with ProvidesHeader {

  /**
    *
    * @param regionId Region id
    * @return
    */
  def getLabelsFromCurrentMission(regionId: Int) = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val labels = LabelTable.getLabelsFromCurrentAuditMission(regionId, user.userId)
        val jsLabels = JsArray(labels.map(l => Json.toJson(l)))
        Future.successful(Ok(jsLabels))
      case None =>
        Future.successful(Redirect(s"/anonSignUp?url=/label/currentMission?regionId=$regionId"))
    }
  }

  /**
    * Fetches the labels that a user has added in the current region they are working in.
    * @param regionId Region id
    * @return A list of labels
    */
  def getLabelsForMiniMap(regionId: Int) = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) =>
        val labels: List[LabelTable.MiniMapResumeMetadata] = LabelTable.resumeMiniMap(regionId, user.userId)
        val jsonList: List[JsObject] = labels.map { label =>
          Json.obj(
            "label_id" -> label.labelId,
            "label_type" -> label.labelType,
            "label_lat" -> label.lat,
            "label_lng" -> label.lng
          )
        }
        val featureCollection: JsObject = Json.obj("labels" -> jsonList)
        Future.successful(Ok(featureCollection))
      case None =>
        Future.successful(Redirect(s"/anonSignUp?url=/label/currentMission?regionId=$regionId"))
    }
  }

  /**
    * Gets the metadata for all the labels in a given GSV panorama ID
    * @param gsvPanoramaId the gavpanoramaId for wanted labels
    * @return List of Label metadata
    */
  def getLabelsInPano(gsvPanoramaId: String) = UserAwareAction.async { implicit request =>
    val labels: List[LabelTable.LabelResumeMetadata] = LabelTable.find(gsvPanoramaId)
    val jsonList: List[JsObject] = labels.map { label =>
      Json.obj(
        "label_id" -> label.labelId,
        "label_type" -> label.labelType,
        "gsv_panorama_id" -> label.gsvPanoramaId,
        "heading" -> label.heading,
        "pitch" -> label.pitch,
        "zoom" -> label.zoom,
        "canvas_x" -> label.canvasX,
        "canvas_y" -> label.canvasY,
        "canvas_width" -> label.canvasWidth,
        "canvas_height" -> label.canvasHeight,
        "image_x" -> label.svImageX,
        "image_y" -> label.svImageY
      )
    }
    val featureCollection: JsObject = Json.obj("labels" -> jsonList)
    Future.successful(Ok(featureCollection))
  }

  /**
    * Gets all tags in the database in JSON.
    *
    * @return
    */
  def getLabelTags() = Action.async { implicit request =>
    Future.successful(Ok(JsArray(TagTable.selectAllTags().map { tag => Json.obj(
      "tag_id" -> tag.tagId,
      "label_type" -> LabelTypeTable.labelTypeIdToLabelType(tag.labelTypeId),
      "tag" -> tag.tag
    )})))
  }
}
