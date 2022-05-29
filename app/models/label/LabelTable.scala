package models.label

import com.vividsolutions.jts.geom.Point
import java.net.{ConnectException, HttpURLConnection, SocketException, URL}
import java.sql.Timestamp
import java.util.UUID
import models.attribute.{GlobalAttributeUserAttributeTable, UserAttributeLabelTable}
import models.audit.{AuditTask, AuditTaskTable}
import models.daos.slick.DBTableDefinitions.UserTable
import models.gsv.GSVDataTable
import models.mission.{Mission, MissionTable}
import models.region.RegionTable
import models.user.{RoleTable, UserRoleTable, UserStatTable}
import models.utils.MyPostgresDriver
import models.utils.MyPostgresDriver.simple._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Play
import play.api.Play.current
import play.api.libs.json.{JsObject, Json}
import scala.collection.mutable.ListBuffer
import scala.slick.jdbc.{GetResult, StaticQuery => Q}
import scala.slick.lifted.ForeignKeyQuery

case class Label(labelId: Int, auditTaskId: Int, missionId: Int, gsvPanoramaId: String, labelTypeId: Int,
                 photographerHeading: Float, photographerPitch: Float, panoramaLat: Float, panoramaLng: Float,
                 deleted: Boolean, temporaryLabelId: Option[Int], timeCreated: Option[Timestamp], tutorial: Boolean,
                 streetEdgeId: Int, agreeCount: Int, disagreeCount: Int, notsureCount: Int, correct: Option[Boolean],
                 severity: Option[Int], temporary: Boolean, description: Option[String])

case class LabelLocation(labelId: Int, auditTaskId: Int, gsvPanoramaId: String, labelType: String, lat: Float, lng: Float)

case class LabelLocationWithSeverity(labelId: Int, auditTaskId: Int, gsvPanoramaId: String, labelType: String,
                                     lat: Float, lng: Float, correct: Option[Boolean], expired: Boolean,
                                     highQualityUser: Boolean, severity: Option[Int])

class LabelTable(tag: slick.lifted.Tag) extends Table[Label](tag, Some("sidewalk"), "label") {
  def labelId = column[Int]("label_id", O.PrimaryKey, O.AutoInc)
  def auditTaskId = column[Int]("audit_task_id", O.NotNull)
  def missionId = column[Int]("mission_id", O.NotNull)
  def gsvPanoramaId = column[String]("gsv_panorama_id", O.NotNull)
  def labelTypeId = column[Int]("label_type_id", O.NotNull)
  def photographerHeading = column[Float]("photographer_heading", O.NotNull)
  def photographerPitch = column[Float]("photographer_pitch", O.NotNull)
  def panoramaLat = column[Float]("panorama_lat", O.NotNull)
  def panoramaLng = column[Float]("panorama_lng", O.NotNull)
  def deleted = column[Boolean]("deleted", O.NotNull)
  def temporaryLabelId = column[Option[Int]]("temporary_label_id", O.Nullable)
  def timeCreated = column[Option[Timestamp]]("time_created", O.Nullable)
  def tutorial = column[Boolean]("tutorial", O.NotNull)
  def streetEdgeId = column[Int]("street_edge_id", O.NotNull)
  def agreeCount = column[Int]("agree_count", O.NotNull)
  def disagreeCount = column[Int]("disagree_count", O.NotNull)
  def notsureCount = column[Int]("notsure_count", O.NotNull)
  def correct = column[Option[Boolean]]("correct", O.Nullable)
  def severity = column[Option[Int]]("severity", O.Nullable)
  def temporary = column[Boolean]("temporary", O.NotNull)
  def description = column[Option[String]]("description", O.Nullable)

  def * = (labelId, auditTaskId, missionId, gsvPanoramaId, labelTypeId, photographerHeading, photographerPitch,
    panoramaLat, panoramaLng, deleted, temporaryLabelId, timeCreated, tutorial, streetEdgeId, agreeCount, disagreeCount,
    notsureCount, correct, severity, temporary, description) <> ((Label.apply _).tupled, Label.unapply)

  def auditTask: ForeignKeyQuery[AuditTaskTable, AuditTask] =
    foreignKey("label_audit_task_id_fkey", auditTaskId, TableQuery[AuditTaskTable])(_.auditTaskId)

  def mission: ForeignKeyQuery[MissionTable, Mission] =
    foreignKey("label_mission_id_fkey", missionId, TableQuery[MissionTable])(_.missionId)

  def labelType: ForeignKeyQuery[LabelTypeTable, LabelType] =
    foreignKey("label_label_type_id_fkey", labelTypeId, TableQuery[LabelTypeTable])(_.labelTypeId)
}

/**
 * Data access object for the label table.
 */
object LabelTable {
  import MyPostgresDriver.plainImplicits._
  
  val db = play.api.db.slick.DB
  val labels = TableQuery[LabelTable]
  val auditTasks = TableQuery[AuditTaskTable]
  val gsvData = TableQuery[GSVDataTable]
  val labelTypes = TableQuery[LabelTypeTable]
  val labelTags = TableQuery[LabelTagTable]
  val tagTable = TableQuery[TagTable]
  val labelPoints = TableQuery[LabelPointTable]
  val labelValidations = TableQuery[LabelValidationTable]
  val missions = TableQuery[MissionTable]
  val regions = TableQuery[RegionTable]
  val users = TableQuery[UserTable]
  val userRoles = TableQuery[UserRoleTable]
  val roleTable = TableQuery[RoleTable]

  val labelsWithoutDeleted = labels.filter(_.deleted === false)
  val neighborhoods = regions.filter(_.deleted === false)

  // Grab city id of database and the associated tutorial street id for the city
  val cityStr: String = Play.configuration.getString("city-id").get
  val tutorialStreetId: Int = Play.configuration.getInt("city-params.tutorial-street-edge-id." + cityStr).get

  // Filters out the labels placed during onboarding (aka panoramas that are used during onboarding
  // Onboarding labels have to be filtered out before a user's labeling frequency is computed
  val labelsWithoutDeletedOrOnboarding = labelsWithoutDeleted.filter(_.tutorial === false)

  case class LabelCountPerDay(date: String, count: Int)

  case class LabelMetadata(labelId: Int, gsvPanoramaId: String, tutorial: Boolean, imageDate: String, heading: Float,
                           pitch: Float, zoom: Int, canvasXY: (Int, Int), canvasWidth: Int, canvasHeight: Int,
                           auditTaskId: Int, userId: String, username: String, timestamp: Option[java.sql.Timestamp],
                           labelTypeKey: String, labelTypeValue: String, severity: Option[Int], temporary: Boolean,
                           description: Option[String], userValidation: Option[Int], validations: Map[String, Int],
                           tags: List[String])

  // NOTE: canvas_x and canvas_y are null when the label is not visible when validation occurs.
  case class LabelValidationMetadata(labelId: Int, labelType: String, gsvPanoramaId: String, imageDate: String,
                                     timestamp: Option[java.sql.Timestamp], heading: Float, pitch: Float, zoom: Int,
                                     canvasX: Int, canvasY: Int, canvasWidth: Int, canvasHeight: Int,
                                     severity: Option[Int], temporary: Boolean, description: Option[String],
                                     userValidation: Option[Int], tags: List[String])

  case class LabelValidationMetadataWithoutTags(labelId: Int, labelType: String, gsvPanoramaId: String,
                                                imageDate: String, timestamp: Option[java.sql.Timestamp],
                                                heading: Float, pitch: Float, zoom: Int, canvasX: Int, canvasY: Int,
                                                canvasWidth: Int, canvasHeight: Int, severity: Option[Int],
                                                temporary: Boolean, description: Option[String],
                                                userValidation: Option[Int])

  case class ResumeLabelMetadata(labelData: Label, labelType: String, pointData: LabelPoint, svImageWidth: Int,
                                 svImageHeight: Int, tagIds: List[Int])

  case class LabelCVMetadata(labelId: Int, panoId: String, labelTypeId: Int, agreeCount: Int, disagreeCount: Int,
                             notsureCount: Int, imageWidth: Option[Int], imageHeight: Option[Int], svImageX: Int,
                             svImageY: Int, canvasWidth: Int, canvasHeight: Int, canvasX: Int, canvasY: Int, zoom: Int,
                             heading: Float, pitch: Float, photographerHeading: Float, photographerPitch: Float)

  implicit val labelMetadataWithValidationConverter = GetResult[LabelMetadata](r =>
    LabelMetadata(
      r.nextInt, r.nextString, r.nextBoolean, r.nextString, r.nextFloat, r.nextFloat, r.nextInt, (r.nextInt, r.nextInt),
      r.nextInt, r.nextInt, r.nextInt, r.nextString, r.nextString, r.nextTimestampOption, r.nextString, r.nextString,
      r.nextIntOption, r.nextBoolean, r.nextStringOption, r.nextIntOption,
      r.nextString.split(',').map(x => x.split(':')).map { y => (y(0), y(1).toInt) }.toMap,
      r.nextStringOption.map(tags => tags.split(",").toList).getOrElse(List())
    )
  )

  implicit val labelValidationMetadataWithoutTagsConverter = GetResult[LabelValidationMetadataWithoutTags](r =>
    LabelValidationMetadataWithoutTags(
      r.nextInt, r.nextString, r.nextString, r.nextString, r.nextTimestampOption, r.nextFloat, r.nextFloat, r.nextInt,
      r.nextInt, r.nextInt, r.nextInt, r.nextInt, r.nextIntOption, r.nextBoolean, r.nextStringOption, r.nextIntOption
    )
  )

  implicit val labelValidationMetadataConverter = GetResult[LabelValidationMetadata](r =>
    LabelValidationMetadata(
      r.nextInt, r.nextString, r.nextString, r.nextString, r.nextTimestampOption, r.nextFloat, r.nextFloat, r.nextInt,
      r.nextInt, r.nextInt, r.nextInt, r.nextInt, r.nextIntOption, r.nextBoolean, r.nextStringOption,
      r.nextIntOption, r.nextStringOption.map(tags => tags.split(",").toList).getOrElse(List())
    )
  )

  implicit val labelLocationConverter = GetResult[LabelLocation](r =>
    LabelLocation(r.nextInt, r.nextInt, r.nextString, r.nextString, r.nextFloat, r.nextFloat))

  implicit val labelSeverityConverter = GetResult[LabelLocationWithSeverity](r =>
    LabelLocationWithSeverity(r.nextInt, r.nextInt, r.nextString, r.nextString, r.nextFloat, r.nextFloat, r.nextBooleanOption, r.nextBoolean, r.nextBoolean, r.nextIntOption))

  implicit val resumeLabelMetadataConverter = GetResult[ResumeLabelMetadata](r =>
    ResumeLabelMetadata(
      Label(r.nextInt, r.nextInt, r.nextInt, r.nextString, r.nextInt, r.nextFloat, r.nextFloat, r.nextFloat,
        r.nextFloat, r.nextBoolean, r.nextIntOption, r.nextTimestampOption, r.nextBoolean, r.nextInt, r.nextInt,
        r.nextInt, r.nextInt, r.nextBooleanOption, r.nextIntOption, r.nextBoolean, r.nextStringOption),
      r.nextString,
      LabelPoint(r.nextInt, r.nextInt, r.nextInt, r.nextInt, r.nextInt, r.nextInt, r.nextFloat, r.nextFloat, r.nextInt,
        r.nextInt, r.nextInt, r.nextFloat, r.nextFloat, r.nextFloatOption, r.nextFloatOption, r.nextGeometryOption[Point], r.nextStringOption),
      r.nextInt, r.nextInt,
      r.nextStringOption.map(tags => tags.split(",").map(_.toInt).toList).getOrElse(List())
    )
  )

  // Valid label type ids for the /validate -- excludes Other and Occlusion labels.
  val valLabelTypeIds: List[Int] = List(1, 2, 3, 4, 7, 9, 10)

  /**
    * This method gets the label date associated with the given label Id.
    *
    * @param labelId Label ID.
    * @return        String representing the image date in form "yyyy-MM-dd".
    */
  def getLabelDate(labelId: Int): String = db.withSession { implicit session =>
    val labelsWithGivenId = for {
      _lb <- labels if _lb.labelId === labelId
    } yield (
      _lb
    )
    val fullDate: String = labelsWithGivenId.first.timeCreated.getOrElse("").toString()
    val endOfDate: Int = fullDate.indexOf(" ")
    fullDate.substring(0, endOfDate.min(fullDate.length())) // only include month, date, and year
  }

  /**
    * This method gets the label date associated with the given panorama Id.
    *
    * @param gsvPanoramaId GSV Panorama ID.
    * @return              String representing the image date in form "yyyy-MM-dd".
    */
  def getLabelDateFromPanoramaId(gsvPanoramaId: String): String = db.withSession { implicit session =>
    val labelsWithGivenId = for {
      _lb <- labels if _lb.gsvPanoramaId === gsvPanoramaId
    } yield (
      _lb
    )

    val fullDate: String = labelsWithGivenId.first.timeCreated.getOrElse("").toString()
    val endOfDate: Int = fullDate.indexOf(" ")
    fullDate.substring(0, endOfDate.min(fullDate.length())) // only include month, date, and year
  }

  /**
    * This method gets the Panorama Id of the image associated with the given global attribute Id.
    *
    * @param globalAttributeId Global Attribute ID.
    * @return                  String representing the GSV Panorama ID of the image.
    */
  def getPanoramaIdFromGlobalAttributeId(globalAttributeId: Int): String = db.withSession { implicit session =>
    val labelsWithGivenGlobalAttributeId = for {
      _gaua <- GlobalAttributeUserAttributeTable.globalAttributeUserAttributes if _gaua.globalAttributeId === globalAttributeId
      _ual <- UserAttributeLabelTable.userAttributeLabels if _gaua.userAttributeId === _ual.userAttributeId
      _lb <- LabelTable.labels if _ual.labelId === _lb.labelId
    } yield (
      _lb
    )
    labelsWithGivenGlobalAttributeId.first.gsvPanoramaId
  }

  /**
    * This method gets the age of the image associated with the given panorama Id.
    *
    * @param gsvPanoramaId GSV Panorama ID.
    * @return              Long representing the image age in seconds.
    */
  def getLabelAgeFromPanoramaId(gsvPanoramaId: String): Long = db.withSession { implicit session =>
    val labelsWithGivenId = for {
      _lb <- labels if _lb.gsvPanoramaId === gsvPanoramaId
    } yield (
      _lb
    )

    val now: Long = System.currentTimeMillis();
    val labelTime: Long = labelsWithGivenId.first.timeCreated.getOrElse(new Timestamp(now)).getTime()
    now - labelTime
  }

  /**
    * Find all labels with given regionId and userId.
    */
  def find(tempLabelId: Int, userId: UUID): Option[Int] = db.withSession { implicit session =>
    (for {
      m <- missions
      l <- labels if l.missionId === m.missionId
      if l.temporaryLabelId === tempLabelId && m.userId === userId.toString
    } yield l.labelId).firstOption
  }

  def countLabels: Int = db.withTransaction(implicit session =>
    labels.filter(_.deleted === false).length.run
  )

  def countLabels(labelTypeString: String): Int = db.withTransaction(implicit session =>
    labels.filter(_.deleted === false).filter(_.labelTypeId === LabelTypeTable.labelTypeToId(labelTypeString)).length.run
  )

  /*
  * Counts the number of labels added today.
  *
  * If the task goes over two days, then all labels for that audit task will be added for the task end date.
  */
  def countTodayLabels: Int = db.withSession { implicit session =>
    val countQuery = Q.queryNA[(Int)](
      """SELECT COUNT(label.label_id)
        |FROM audit_task
        |INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
        |WHERE (audit_task.task_end AT TIME ZONE 'US/Pacific')::date = (now() AT TIME ZONE 'US/Pacific')::date
        |    AND label.deleted = false""".stripMargin
    )
    countQuery.first
  }

  /*
  * Counts the number of specific label types added today.
  *
  * If the task goes over two days, then all labels for that audit task will be added for the task end date.
  */
  def countTodayLabels(labelType: String): Int = db.withSession { implicit session =>
    val countQuery = Q.queryNA[Int](
      s"""SELECT COUNT(label.label_id)
         |FROM audit_task
         |INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
         |WHERE (audit_task.task_end AT TIME ZONE 'US/Pacific')::date = (now() AT TIME ZONE 'US/Pacific')::date
         |    AND label.deleted = false
         |    AND label.label_type_id = (
         |        SELECT label_type_id
         |        FROM label_type as lt
         |        WHERE lt.label_type='$labelType'
         |    )""".stripMargin
    )
    countQuery.first
  }

  /*
  * Counts the number of labels added during the last week.
  */
  def countPastWeekLabels: Int = db.withTransaction { implicit session =>
    val countQuery = Q.queryNA[(Int)](
      """SELECT COUNT(label.label_id)
        |FROM audit_task
        |INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
        |WHERE (audit_task.task_end AT TIME ZONE 'US/Pacific') > (now() AT TIME ZONE 'US/Pacific') - interval '168 hours'
        |    AND label.deleted = false""".stripMargin
    )
    countQuery.first
  }

  /*
  * Counts the number of specific label types added during the last week.
  */
  def countPastWeekLabels(labelType: String): Int = db.withTransaction { implicit session =>
    val countQuery = Q.queryNA[Int](
      s"""SELECT COUNT(label.label_id)
         |FROM audit_task
         |INNER JOIN label ON label.audit_task_id = audit_task.audit_task_id
         |WHERE (audit_task.task_end AT TIME ZONE 'US/Pacific') > (now() AT TIME ZONE 'US/Pacific') - interval '168 hours'
         |    AND label.deleted = false
         |    AND label.label_type_id = (
         |        SELECT label_type_id
         |        FROM label_type as lt
         |        WHERE lt.label_type='$labelType'
         |    )""".stripMargin
    )
    countQuery.first
  }

  /**
    * Returns the number of labels submitted by the given user.
    *
    * @param userId User id
    * @return A number of labels submitted by the user
    */
  def countLabels(userId: UUID): Int = db.withSession { implicit session =>
    val tasks = auditTasks.filter(_.userId === userId.toString)
    val _labels = for {
      (_tasks, _labels) <- tasks.innerJoin(labelsWithoutDeletedOrOnboarding).on(_.auditTaskId === _.auditTaskId)
    } yield _labels
    _labels.length.run
  }

  /**
   * Update the metadata that users might change after initially placing the label.
   *
   * @param labelId
   * @param deleted
   * @param severity
   * @param temporary
   * @param description
   * @return
   */
  def update(labelId: Int, deleted: Boolean, severity: Option[Int], temporary: Boolean, description: Option[String]): Int = db.withSession { implicit session =>
    labels
      .filter(_.labelId === labelId)
      .map(l => (l.deleted, l.severity, l.temporary, l.description))
      .update((deleted, severity, temporary, description))
  }

  /**
   * Saves a new label in the table.
   */
  def save(label: Label): Int = db.withTransaction { implicit session =>
    val labelId: Int =
      (labels returning labels.map(_.labelId)) += label
    labelId
  }

  /**
   * Gets metadata for the `takeN` most recent labels. Optionally filter by user_id of the labeler.
   *
   * @param takeN Number of labels to retrieve
   * @param labelerId user_id of the person who placed the labels; an optional filter
   * @param validatorId optionally include this user's validation info for each label in the userValidation field
   * @param labelId optionally include this if you only want the metadata for the single given label
   * @return
   */
  def getRecentLabelsMetadata(takeN: Int, labelerId: Option[String] = None, validatorId: Option[String] = None, labelId: Option[Int] = None): List[LabelMetadata] = db.withSession { implicit session =>
    // Optional filter to only get labels placed by the given user.
    val labelerFilter: String = if (labelerId.isDefined) s"""AND u.user_id = '${labelerId.get}'""" else ""

    // Optionally include the given user's validation info for each label in the userValidation field.
    val validatorJoin: String =
      if (validatorId.isDefined) {
        s"""LEFT JOIN (
           |    SELECT label_id, validation_result
           |    FROM label_validation WHERE user_id = '${validatorId.get}'
           |) AS user_validation ON lb.label_id = user_validation.label_id""".stripMargin
      } else {
        "LEFT JOIN ( SELECT NULL AS validation_result ) AS user_validation ON lb.label_id = NULL"
      }

    // Either filter for the given labelId or filter out deleted and tutorial labels.
    val labelFilter: String = if (labelId.isDefined) {
      s"""AND lb1.label_id = ${labelId.get}"""
    } else {
      "AND lb1.deleted = FALSE AND lb1.tutorial = FALSE"
    }

    val selectQuery = Q.queryNA[LabelMetadata](
      s"""SELECT lb1.label_id,
        |       lb1.gsv_panorama_id,
        |       lb1.tutorial,
        |       gsv_data.image_date,
        |       lp.heading,
        |       lp.pitch,
        |       lp.zoom,
        |       lp.canvas_x,
        |       lp.canvas_y,
        |       lp.canvas_width,
        |       lp.canvas_height,
        |       lb1.audit_task_id,
        |       u.user_id,
        |       u.username,
        |       lb1.time_created,
        |       lb_big.label_type,
        |       lb_big.label_type_desc,
        |       lb_big.severity,
        |       lb_big.temporary,
        |       lb_big.description,
        |       lb_big.validation_result,
        |       val.val_counts,
        |       lb_big.tag_list
        |FROM label AS lb1,
        |     gsv_data,
        |     audit_task AS at,
        |     sidewalk_user AS u,
        |     label_point AS lp,
        |     (
        |         SELECT lb.label_id,
        |                lb.gsv_panorama_id,
        |                lbt.label_type,
        |                lbt.description AS label_type_desc,
        |                lb.severity,
        |                lb.temporary,
        |                lb.description,
        |                user_validation.validation_result,
        |                the_tags.tag_list
        |         FROM label AS lb
        |         LEFT JOIN label_type as lbt ON lb.label_type_id = lbt.label_type_id
        |         $validatorJoin
        |         LEFT JOIN (
        |             SELECT label_id, array_to_string(array_agg(tag.tag), ',') AS tag_list
        |             FROM label_tag
        |             INNER JOIN tag ON label_tag.tag_id = tag.tag_id
        |             GROUP BY label_id
        |         ) AS the_tags
        |             ON lb.label_id = the_tags.label_id
        |     ) AS lb_big,
        |     (
        |         SELECT label_id,
        |                CONCAT('agree:', CAST(agree_count AS TEXT),
        |                       ',disagree:', CAST(disagree_count AS TEXT),
        |                       ',notsure:', CAST(notsure_count AS TEXT)) AS val_counts
        |         FROM label
        |     ) AS val
        |WHERE lb1.gsv_panorama_id = gsv_data.gsv_panorama_id
        |    AND lb1.audit_task_id = at.audit_task_id
        |    AND lb1.label_id = lb_big.label_id
        |    AND at.user_id = u.user_id
        |    AND lb1.label_id = lp.label_id
        |    AND lb1.label_id = val.label_id
        |    $labelFilter
        |    $labelerFilter
        |ORDER BY lb1.label_id DESC
        |LIMIT $takeN""".stripMargin
    )
    selectQuery.list
  }

  /**
   * Gets the metadata for the label with the given `labelId`.
   * @param labelId
   * @param userId
   * @return
   */
  def getSingleLabelMetadata(labelId: Int, userId: String): LabelMetadata = {
    getRecentLabelsMetadata(1, None, Some(userId), Some(labelId)).head
  }

  /**
    * Returns how many labels this user has available to validate for each label type.
    *
    * @return List[(label_type_id, label_count)]
    */
  def getAvailableValidationLabelsByType(userId: UUID): List[(Int, Int)] = db.withSession { implicit session =>
    val userIdString: String = userId.toString
    val labelsValidatedByUser = labelValidations.filter(_.userId === userIdString)

    // Get labels the given user has not placed that have non-expired GSV imagery.
    val labelsToValidate =  for {
      _lb <- labels if _lb.deleted === false && _lb.tutorial === false
      _gd <- gsvData if _gd.gsvPanoramaId === _lb.gsvPanoramaId && _gd.expired === false
      _ms <- missions if _ms.missionId === _lb.missionId && _ms.userId =!= userIdString
      _a <- auditTasks if _lb.auditTaskId === _a.auditTaskId && _a.streetEdgeId =!= tutorialStreetId
      _us <- UserStatTable.userStats if _ms.userId === _us.userId
      if _us.highQuality
    } yield (_lb.labelId, _lb.labelTypeId)

    // Left join with the labels that the user has already validated, then filter those out.
    val filteredLabelsToValidate = for {
      (_lab, _val) <- labelsToValidate.leftJoin(labelsValidatedByUser).on(_._1 === _.labelId)
      if _val.labelId.?.isEmpty
    } yield _lab

    // Group by the label_type_id and count.
    filteredLabelsToValidate.groupBy(_._2).map{ case (labType, group) => (labType, group.length) }.list
  }

  /**
    * Retrieve n random labels that have existing GSVPanorama.
    *
    * Starts by querying for n * 5 labels, then checks GSV API to see if each gsv_panorama_id exists until we find n.
    *
    * @param userId         User ID for the current user.
    * @param n              Number of labels we need to query.
    * @param labelTypeId    Label Type ID of labels requested.
    * @param skippedLabelId Label ID of the label that was just skipped (if applicable).
    * @return               Seq[LabelValidationMetadata]
    */
  def retrieveLabelListForValidation(userId: UUID, n: Int, labelTypeId: Int, skippedLabelId: Option[Int]): Seq[LabelValidationMetadata] = db.withSession { implicit session =>
    var selectedLabels: ListBuffer[LabelValidationMetadata] = new ListBuffer[LabelValidationMetadata]()
    var potentialLabels: List[LabelValidationMetadata] = List()
    val userIdStr = userId.toString

    while (selectedLabels.length < n) {
      val selectRandomLabelsQuery = Q.queryNA[LabelValidationMetadata] (
        s"""SELECT label.label_id, label_type.label_type, label.gsv_panorama_id, gsv_data.image_date,
          |        label.time_created, label_point.heading, label_point.pitch, label_point.zoom, label_point.canvas_x,
          |        label_point.canvas_y, label_point.canvas_width, label_point.canvas_height, label.severity,
          |        label.temporary, label.description, user_validation.validation_result, the_tags.tag_list
          |FROM label
          |INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
          |INNER JOIN label_point ON label.label_id = label_point.label_id
          |INNER JOIN gsv_data ON label.gsv_panorama_id = gsv_data.gsv_panorama_id
          |INNER JOIN mission ON label.mission_id = mission.mission_id
          |INNER JOIN user_stat ON mission.user_id = user_stat.user_id
          |INNER JOIN audit_task ON label.audit_task_id = audit_task.audit_task_id
          |LEFT JOIN (
          |    -- This subquery counts how many of each users' labels have been validated. If it's less than 50, then we
          |    -- need more validations from them in order to infer worker quality, and they therefore get priority.
          |    SELECT mission.user_id,
          |           COUNT(CASE WHEN label.correct IS NOT NULL THEN 1 END) < 50 AS needs_validations
          |    FROM mission
          |    INNER JOIN label ON label.mission_id = mission.mission_id
          |    WHERE label.deleted = FALSE
          |        AND label.tutorial = FALSE
          |    GROUP BY mission.user_id
          |) needs_validations_query ON mission.user_id = needs_validations_query.user_id
          |LEFT JOIN (
          |    -- Puts set of tag_ids associated with the label in a comma-separated list in a string.
          |    SELECT label_id, array_to_string(array_agg(tag.tag), ',') AS tag_list
          |    FROM label_tag
          |    INNER JOIN tag ON label_tag.tag_id = tag.tag_id
          |    GROUP BY label_id
          |) the_tags ON label.label_id = the_tags.label_id
          |LEFT JOIN (
          |    -- Gets the validations from this user. Since we only want them to validate labels that
          |    -- they've never validated, when we left join, we should only get nulls from this query.
          |    SELECT label_id, validation_result
          |    FROM label_validation
          |    WHERE user_id = '$userIdStr'
          |) user_validation ON label.label_id = user_validation.label_id
          |WHERE label.label_type_id = $labelTypeId
          |    AND label.deleted = FALSE
          |    AND label.tutorial = FALSE
          |    AND label.street_edge_id <> $tutorialStreetId
          |    AND audit_task.street_edge_id <> $tutorialStreetId
          |    AND gsv_data.expired = FALSE
          |    AND mission.user_id <> '$userIdStr'
          |    AND user_stat.high_quality = TRUE
          |    AND label.label_id NOT IN (
          |        SELECT label_id
          |        FROM label_validation
          |        WHERE user_id = '$userIdStr'
          |    )
          |-- Prioritize labels that have been validated fewer times and from users who have had less than 50
          |-- validations of this label type, then randomize it.
          |ORDER BY label.agree_count + label.disagree_count + label.notsure_count, COALESCE(needs_validations, TRUE) DESC, RANDOM()
          |LIMIT ${n * 5}""".stripMargin
      )
      potentialLabels = selectRandomLabelsQuery.list

      // Remove label that was just skipped (if one was skipped).
      potentialLabels = potentialLabels.filter(_.labelId != skippedLabelId.getOrElse(-1))

      // Randomize those n * 5 high priority labels to prevent repeated and similar labels in a mission.
      // https://github.com/ProjectSidewalk/SidewalkWebpage/issues/1874
      // https://github.com/ProjectSidewalk/SidewalkWebpage/issues/1823
      potentialLabels = scala.util.Random.shuffle(potentialLabels)

      var potentialStartIdx: Int = 0

      // Start looking through our n * 5 labels until we find n with valid pano id or we've gone through our n * 5 and
      // need to query for some more (which we don't expect to happen in a typical use case).
      while (selectedLabels.length < n && potentialStartIdx < potentialLabels.length) {

        val labelsNeeded: Int = n - selectedLabels.length
        val newLabels: Seq[LabelValidationMetadata] =
          potentialLabels.slice(potentialStartIdx, potentialStartIdx + labelsNeeded).par.flatMap { currLabel =>

            // If the pano exists, mark the last time we viewed it in the database, o/w mark as expired.
            if (panoExists(currLabel.gsvPanoramaId)) {
              val now = new DateTime(DateTimeZone.UTC)
              val timestamp: Timestamp = new Timestamp(now.getMillis)
              GSVDataTable.markLastViewedForPanorama(currLabel.gsvPanoramaId, timestamp)
              Some(currLabel)
            } else {
              GSVDataTable.markExpired(currLabel.gsvPanoramaId, expired = true)
              None
            }
          }.seq

        potentialStartIdx += labelsNeeded
        selectedLabels ++= newLabels
      }
    }
    selectedLabels
  }

  /**
   * Retrieves n labels of specified type, severities, and tags.
   *
   * @param labelTypeId Label type specifying what type of labels to grab.
   * @param n Number of labels to grab.
   * @param loadedLabelIds Set of labelIds already grabbed as to not grab them again.
   * @param severity  Set of severities the labels grabbed can have.
   * @param tags Set of tags the labels grabbed can have.
   * @return Seq[LabelValidationMetadata]
   */
  def getLabelsOfTypeBySeverityAndTags(labelTypeId: Int, n: Int, loadedLabelIds: Set[Int], severity: Set[Int], tags: Set[String], userId: UUID): Seq[LabelValidationMetadata] = db.withSession { implicit session =>
    // List to return.
    val selectedLabels: ListBuffer[LabelValidationMetadata] = new ListBuffer[LabelValidationMetadata]()

    // Init random function.
    val rand = SimpleFunction.nullary[Double]("random")

    // Get deprioritized labels.
    val deprioritized = deprioritizedLabels()

    // Grab labels and associated information if severity and tags satisfy query conditions.
    val _labelsUnfiltered = for {
      _lb <- labelsWithoutDeletedOrOnboarding if !(_lb.labelId inSet deprioritized)
      _lt <- labelTypes if _lb.labelTypeId === _lt.labelTypeId
      _lp <- labelPoints if _lb.labelId === _lp.labelId
      _labeltags <- labelTags if _lb.labelId === _labeltags.labelId
      _tags <- tagTable if _labeltags.tagId === _tags.tagId && ((_tags.tag inSet tags) || tags.isEmpty)
      _a <- auditTasks if _lb.auditTaskId === _a.auditTaskId && _a.streetEdgeId =!= tutorialStreetId
      _us <- UserStatTable.userStats if _a.userId === _us.userId
      if _lb.labelTypeId === labelTypeId && _lb.streetEdgeId =!= tutorialStreetId
      if _us.highQuality
      if _lb.severity.isEmpty || (_lb.severity inSet severity)
    } yield (_lb, _lp, _lt.labelType)

    // Could be optimized by grouping on fewer columns.
    val _labelsGrouped = _labelsUnfiltered.groupBy(x => x).map(_._1)

    // Filter out labels already grabbed before.
    val _labels = _labelsGrouped.filter(label => !(label._1.labelId inSet loadedLabelIds))

    // Join with gsvData to add gsv data.
    val addGSVData = for {
      (l, e) <- _labels.leftJoin(gsvData).on(_._1.gsvPanoramaId === _.gsvPanoramaId)
    } yield (l._1, l._2, l._3, e.imageDate, e.expired)

    // Remove labels with expired panos.
    val removeExpiredPanos = addGSVData.filter(_._5 === false)

    // Join with the validations that the user has given.
    val userValidations = validationsFromUser(userId)
    val addValidations = for {
      (l, v) <- removeExpiredPanos.leftJoin(userValidations).on(_._1.labelId === _._1)
    } yield (l._1.labelId, l._3, l._1.gsvPanoramaId, l._4, l._1.timeCreated, l._2.heading, l._2.pitch, l._2.zoom,
      l._2.canvasX, l._2.canvasY, l._2.canvasWidth, l._2.canvasHeight, l._1.severity, l._1.temporary, l._1.description,
      v._2.?)

    // Randomize and convert to LabelValidationMetadataWithoutTags.
    val newRandomLabelsList = addValidations.sortBy(x => rand).list.map(LabelValidationMetadataWithoutTags.tupled)

    var potentialStartIdx: Int = 0

    // While the desired query size has not been met and there are still possibly valid labels to consider, traverse
    // through the list incrementally and see if a potentially valid label has pano data for viewability.
    while (selectedLabels.length < n && potentialStartIdx < newRandomLabelsList.size) {
      val labelsNeeded: Int = n - selectedLabels.length
      val newLabels: Seq[LabelValidationMetadata] =
        newRandomLabelsList.slice(potentialStartIdx, potentialStartIdx + labelsNeeded).par.flatMap { currLabel =>

          // If the pano exists, mark the last time we viewed it in the database, o/w mark as expired.
          if (panoExists(currLabel.gsvPanoramaId)) {
            val now = new DateTime(DateTimeZone.UTC)
            val timestamp: Timestamp = new Timestamp(now.getMillis)
            GSVDataTable.markLastViewedForPanorama(currLabel.gsvPanoramaId, timestamp)
            val tagsToCheck: List[String] = getTagsFromLabelId(currLabel.labelId)
            if (tagsToCheck.exists(tags.contains(_)) || tags.isEmpty) {
              Some(labelAndTagsToLabelValidationMetadata(currLabel, tagsToCheck))
            } else {
              None
            }
          } else {
            GSVDataTable.markExpired(currLabel.gsvPanoramaId, expired = true)
            None
          }
        }.seq
      potentialStartIdx += labelsNeeded
      selectedLabels ++= newLabels
    }
    selectedLabels
  }

  /**
   * Retrieve n random labels of assorted types.
   *
   * @param n Number of labels to grab.
   * @param loadedLabelIds Label Ids of labels already grabbed.
   * @param severity Optional set of severities the labels grabbed can have.
   * @return Seq[LabelValidationMetadata]
   */
  def getAssortedLabels(n: Int, loadedLabelIds: Set[Int], userId: UUID, severity: Option[Set[Int]] = None): Seq[LabelValidationMetadata] = db.withSession { implicit session =>
    // List to return.
    val selectedLabels: ListBuffer[LabelValidationMetadata] = new ListBuffer[LabelValidationMetadata]()

    // Init random function.
    val rand = SimpleFunction.nullary[Double]("random")

    // Get deprioritized labels.
    val deprioritized = deprioritizedLabels()

    // Grab labels and associated information if severity and tags satisfy query conditions.
    val _labelsUnfiltered = for {
      _lb <- labelsWithoutDeletedOrOnboarding if !(_lb.labelId inSet deprioritized)
      _lt <- labelTypes if _lb.labelTypeId === _lt.labelTypeId && (_lt.labelTypeId inSet LabelTypeTable.primaryLabelTypeIds)
      _lp <- labelPoints if _lb.labelId === _lp.labelId
      _a <- auditTasks if _lb.auditTaskId === _a.auditTaskId && _a.streetEdgeId =!= tutorialStreetId
      _us <- UserStatTable.userStats if _a.userId === _us.userId
      if _lb.streetEdgeId =!= tutorialStreetId
      if _us.highQuality
    } yield (_lb, _lp, _lt.labelType)

    // If severities are specified, filter by whether a label has a valid severity.
    val _labelsPartiallyFiltered = if (severity.isDefined && severity.get.nonEmpty)
      _labelsUnfiltered.filter(_._1.severity inSet severity.get)
    else
      _labelsUnfiltered

    // Filter out labels already grabbed before.
    val _labels = _labelsPartiallyFiltered.filter(label => !(label._1.labelId inSet loadedLabelIds))

    // Join with gsvData to add gsv data.
    val addGSVData = for {
      (l, e) <- _labels.leftJoin(gsvData).on(_._1.gsvPanoramaId === _.gsvPanoramaId)
    } yield (l._1, l._2, l._3, e.imageDate, e.expired)

    // Remove labels with expired panos.
    val removeExpiredPanos = addGSVData.filter(_._5 === false)

    // Join with the validations that the user has given.
    val userValidations = validationsFromUser(userId)
    val addValidations = for {
      (l, v) <- removeExpiredPanos.leftJoin(userValidations).on(_._1.labelId === _._1)
    } yield (l._1.labelId, l._3, l._1.gsvPanoramaId, l._4, l._1.timeCreated, l._2.heading, l._2.pitch,
      l._2.zoom, l._2.canvasX, l._2.canvasY, l._2.canvasWidth, l._2.canvasHeight, l._1.severity, l._1.temporary,
      l._1.description, v._2.?)

    // Randomize and convert to LabelValidationMetadataWithoutTags.
    val newRandomLabelsList = addValidations.sortBy(x => rand).list.map(LabelValidationMetadataWithoutTags.tupled)

    val labelTypesAsStrings = LabelTypeTable.primaryLabelTypes

    for (labelType <- labelTypesAsStrings) {
      val labelsFilteredByType = newRandomLabelsList.filter(label => label.labelType == labelType)
      val selectedLabelsOfType: ListBuffer[LabelValidationMetadata] = new ListBuffer[LabelValidationMetadata]()
      var potentialStartIdx: Int = 0

      while (selectedLabelsOfType.length < (n / labelTypesAsStrings.size) + 1 && potentialStartIdx < labelsFilteredByType.size) {
        val labelsNeeded: Int = (n / labelTypesAsStrings.size) + 1 - selectedLabelsOfType.length
        val newLabels: Seq[LabelValidationMetadata] =
          labelsFilteredByType.slice(potentialStartIdx, potentialStartIdx + labelsNeeded).par.flatMap { currLabel =>

            // If the pano exists, mark the last time we viewed it in the database, o/w mark as expired.
            if (panoExists(currLabel.gsvPanoramaId)) {
              val now = new DateTime(DateTimeZone.UTC)
              val timestamp: Timestamp = new Timestamp(now.getMillis)
              GSVDataTable.markLastViewedForPanorama(currLabel.gsvPanoramaId, timestamp)
              Some(labelAndTagsToLabelValidationMetadata(currLabel, getTagsFromLabelId(currLabel.labelId)))
            } else {
              GSVDataTable.markExpired(currLabel.gsvPanoramaId, expired = true)
              None
            }
          }.seq
        potentialStartIdx += labelsNeeded
        selectedLabelsOfType ++= newLabels
      }
      selectedLabels ++= selectedLabelsOfType
    }
    selectedLabels
  }

  /**
   * Retrieve n random labels of a specified type.
   *
   * @param labelTypeId Label Type ID of labels requested.
   * @param n Number of labels to grab.
   * @param loadedLabelIds Label Ids of labels already grabbed.
   * @return Seq[LabelValidationMetadata]
   */
  def getLabelsByType(labelTypeId: Int, n: Int, loadedLabelIds: Set[Int], userId: UUID): Seq[LabelValidationMetadata] = db.withSession { implicit session =>
    // List to return.
    val selectedLabels: ListBuffer[LabelValidationMetadata] = new ListBuffer[LabelValidationMetadata]()

    // Init random function.
    val rand = SimpleFunction.nullary[Double]("random")

    // Get deprioritized labels.
    val deprioritized = deprioritizedLabels()

    // Grab labels and associated information if severity and tags satisfy query conditions.
    val _labelsUnfiltered = for {
      _lb <- labelsWithoutDeletedOrOnboarding if !(_lb.labelId inSet deprioritized)
      _lt <- labelTypes if _lb.labelTypeId === _lt.labelTypeId
      _lp <- labelPoints if _lb.labelId === _lp.labelId
      _a <- auditTasks if _lb.auditTaskId === _a.auditTaskId && _a.streetEdgeId =!= tutorialStreetId
      _us <- UserStatTable.userStats if _a.userId === _us.userId
      if _lb.labelTypeId === labelTypeId && _lb.streetEdgeId =!= tutorialStreetId
      if _us.highQuality
    } yield (_lb, _lp, _lt.labelType)

    // Filter out labels already grabbed before.
    val _labels = _labelsUnfiltered.filter(label => !(label._1.labelId inSet loadedLabelIds))

    // Join with gsvData to add gsv data.
    val addGSVData = for {
      (l, e) <- _labels.leftJoin(gsvData).on(_._1.gsvPanoramaId === _.gsvPanoramaId)
    } yield (l._1, l._2, l._3, e.imageDate, e.expired)

    // Remove labels with expired panos.
    val removeExpiredPanos = addGSVData.filter(_._5 === false)

    // Join with the validations that the user has given.
    val userValidations = validationsFromUser(userId)
    val addValidations = for {
      (l, v) <- removeExpiredPanos.leftJoin(userValidations).on(_._1.labelId === _._1)
    } yield (l._1.labelId, l._3, l._1.gsvPanoramaId, l._4, l._1.timeCreated, l._2.heading, l._2.pitch,
      l._2.zoom, l._2.canvasX, l._2.canvasY, l._2.canvasWidth, l._2.canvasHeight, l._1.severity, l._1.temporary,
      l._1.description, v._2.?)

    // Randomize and convert to LabelValidationMetadataWithoutTags.
    val newRandomLabelsList = addValidations.sortBy(x => rand).list.map(LabelValidationMetadataWithoutTags.tupled)

    var potentialStartIdx: Int = 0

    // While the desired query size has not been met and there are still possibly valid labels to consider, traverse
    // through the list incrementally and see if a potentially valid label has pano data for viewability.
    while (selectedLabels.length < n && potentialStartIdx < newRandomLabelsList.size) {
      val labelsNeeded: Int = n - selectedLabels.length
      val newLabels: Seq[LabelValidationMetadata] =
        newRandomLabelsList.slice(potentialStartIdx, potentialStartIdx + labelsNeeded).par.flatMap { currLabel =>

          // If the pano exists, mark the last time we viewed it in the database, o/w mark as expired.
          if (panoExists(currLabel.gsvPanoramaId)) {
            val now = new DateTime(DateTimeZone.UTC)
            val timestamp: Timestamp = new Timestamp(now.getMillis)
            GSVDataTable.markLastViewedForPanorama(currLabel.gsvPanoramaId, timestamp)
            Some(labelAndTagsToLabelValidationMetadata(currLabel, getTagsFromLabelId(currLabel.labelId)))
          } else {
            GSVDataTable.markExpired(currLabel.gsvPanoramaId, expired = true)
            None
          }
        }.seq

      potentialStartIdx += labelsNeeded
      selectedLabels ++= newLabels
    }
    selectedLabels
  }

  /**
   * A query to get all validations by the given user.
   *
   * @param userId
   * @return A query with the integer columns label_id and validation_result
   */
  def validationsFromUser(userId: UUID): Query[(Column[Int], Column[Int]), (Int, Int), Seq] = {
    labelValidations.filter(_.userId === userId.toString).map(v => (v.labelId, v.validationResult))
  }

  /**
    * Returns a LabelValidationMetadata object that has the label properties as well as the tags.
    *
    * @param label label from query
    * @param tags list of tags as strings
    * @return LabelValidationMetadata object
    */
  def labelAndTagsToLabelValidationMetadata(label: LabelValidationMetadataWithoutTags, tags: List[String]): LabelValidationMetadata = {
      LabelValidationMetadata(
        label.labelId, label.labelType, label.gsvPanoramaId, label.imageDate, label.timestamp, label.heading,
        label.pitch, label.zoom, label.canvasX, label.canvasY, label.canvasWidth, label.canvasHeight, label.severity,
        label.temporary, label.description, label.userValidation, tags
      )
  }

  /**
    * Retrieves a list of possible label types that the user can validate.
    *
    * We do this by getting the number of labels available to validate for each label type. We then filter out label
    * types with less than 10 labels to validate (the size of a validation mission), and we filter for labels in our
    * labelTypeIdList (the main label types that we ask users to validate).
    *
    * @param userId               User ID of the current user.
    * @param count                Number of labels for this mission.
    * @param currentLabelTypeId   Label ID of the current mission
    */
  def retrievePossibleLabelTypeIds(userId: UUID, count: Int, currentLabelTypeId: Option[Int]): List[Int] = {
    getAvailableValidationLabelsByType(userId).filter(_._2 > count * 2).map(_._1).filter(valLabelTypeIds.contains(_))
  }

    /**
    * Checks if the panorama associated with a label exists by pinging Google Maps.
    *
    * @param gsvPanoId  Panorama ID
    * @return           True if the panorama exists, false otherwise
    */
  def panoExists(gsvPanoId: String): Boolean = {
    try {
      val now = new DateTime(DateTimeZone.UTC)
      val urlString : String = "http://maps.google.com/cbk?output=tile&panoid=" + gsvPanoId + "&zoom=1&x=0&y=0&date=" + now.getMillis
      val panoURL : URL = new java.net.URL(urlString)
      val connection : HttpURLConnection = panoURL.openConnection.asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(5000)
      connection.setReadTimeout(5000)
      connection.setRequestMethod("GET")
      val responseCode: Int = connection.getResponseCode

      // URL is only valid if the response code is between 200 and 399.
      200 <= responseCode && responseCode <= 399
    } catch {
      case e: ConnectException => false
      case e: SocketException => false
      case e: Exception => false
    }
  }

  def validationLabelMetadataToJson(labelMetadata: LabelValidationMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "label_type" -> labelMetadata.labelType,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "image_date" -> labelMetadata.imageDate,
      "label_timestamp" -> labelMetadata.timestamp,
      "heading" -> labelMetadata.heading,
      "pitch" -> labelMetadata.pitch,
      "zoom" -> labelMetadata.zoom,
      "canvas_x" -> labelMetadata.canvasX,
      "canvas_y" -> labelMetadata.canvasY,
      "canvas_width" -> labelMetadata.canvasWidth,
      "canvas_height" -> labelMetadata.canvasHeight,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "tags" -> labelMetadata.tags
    )
  }

  def labelMetadataWithValidationToJsonAdmin(labelMetadata: LabelMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "tutorial" -> labelMetadata.tutorial,
      "image_date" -> labelMetadata.imageDate,
      "heading" -> labelMetadata.heading,
      "pitch" -> labelMetadata.pitch,
      "zoom" -> labelMetadata.zoom,
      "canvas_x" -> labelMetadata.canvasXY._1,
      "canvas_y" -> labelMetadata.canvasXY._2,
      "canvas_width" -> labelMetadata.canvasWidth,
      "canvas_height" -> labelMetadata.canvasHeight,
      "audit_task_id" -> labelMetadata.auditTaskId,
      "user_id" -> labelMetadata.userId,
      "username" -> labelMetadata.username,
      "timestamp" -> labelMetadata.timestamp,
      "label_type_key" -> labelMetadata.labelTypeKey,
      "label_type_value" -> labelMetadata.labelTypeValue,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "num_agree" -> labelMetadata.validations("agree"),
      "num_disagree" -> labelMetadata.validations("disagree"),
      "num_notsure" -> labelMetadata.validations("notsure"),
      "tags" -> labelMetadata.tags
    )
  }
  // Has the label metadata excluding username, user_id, and audit_task_id.
  def labelMetadataWithValidationToJson(labelMetadata: LabelMetadata): JsObject = {
    Json.obj(
      "label_id" -> labelMetadata.labelId,
      "gsv_panorama_id" -> labelMetadata.gsvPanoramaId,
      "tutorial" -> labelMetadata.tutorial,
      "image_date" -> labelMetadata.imageDate,
      "heading" -> labelMetadata.heading,
      "pitch" -> labelMetadata.pitch,
      "zoom" -> labelMetadata.zoom,
      "canvas_x" -> labelMetadata.canvasXY._1,
      "canvas_y" -> labelMetadata.canvasXY._2,
      "canvas_width" -> labelMetadata.canvasWidth,
      "canvas_height" -> labelMetadata.canvasHeight,
      "timestamp" -> labelMetadata.timestamp,
      "label_type_key" -> labelMetadata.labelTypeKey,
      "label_type_value" -> labelMetadata.labelTypeValue,
      "severity" -> labelMetadata.severity,
      "temporary" -> labelMetadata.temporary,
      "description" -> labelMetadata.description,
      "user_validation" -> labelMetadata.userValidation.map(LabelValidationTable.validationOptions.get),
      "num_agree" -> labelMetadata.validations("agree"),
      "num_disagree" -> labelMetadata.validations("disagree"),
      "num_notsure" -> labelMetadata.validations("notsure"),
      "tags" -> labelMetadata.tags
    )
  }

  /**
    * This method returns a list of strings with all the tags associated with a label
    *
    * @return A list of strings with all the tags associated with a label.
    */
  def getTagsFromLabelId(labelId: Int): List[String] = db.withSession { implicit session =>
      val getTagsQuery = Q.query[Int, (String)](
        """SELECT tag
          |FROM tag
          |WHERE tag.tag_id IN
          |(
          |    SELECT tag_id
          |    FROM label_tag
          |    WHERE label_tag.label_id = ?
          |)""".stripMargin
      )
      getTagsQuery(labelId).list
  }

  /**
    * Returns all the submitted labels with their severities included.
    */
  def selectLocationsAndSeveritiesOfLabels: List[LabelLocationWithSeverity] = db.withSession { implicit session =>
    val _labels = for {
      _l <- labelsWithoutDeletedOrOnboarding
      _lType <- labelTypes if _l.labelTypeId === _lType.labelTypeId
      _lPoint <- labelPoints if _l.labelId === _lPoint.labelId
      _gsv <- gsvData if _l.gsvPanoramaId === _gsv.gsvPanoramaId
      _at <- auditTasks if _l.auditTaskId === _at.auditTaskId
      _us <- UserStatTable.userStats if _at.userId === _us.userId
      if _lPoint.lat.isDefined && _lPoint.lng.isDefined // Make sure they are NOT NULL so we can safely use .get later.
      if _l.streetEdgeId =!= tutorialStreetId // Make sure they're not on the tutorial street.
    } yield (_l.labelId, _l.auditTaskId, _l.gsvPanoramaId, _lType.labelType, _lPoint.lat.get, _lPoint.lng.get, _l.correct, _gsv.expired, _us.highQuality, _l.severity)

    _labels.list.map(LabelLocationWithSeverity.tupled)
  }

  /**
    * Retrieve Label Locations within a given bounding box.
    */
  def selectLocationsOfLabelsIn(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): List[LabelLocation] = db.withSession { implicit session =>
    val selectLabelLocationQuery = Q.query[(Double, Double, Double, Double), LabelLocation](
      """SELECT label.label_id,
        |       label.audit_task_id,
        |       label.gsv_panorama_id,
        |       label_type.label_type,
        |       label_point.lat,
        |       label_point.lng
        |FROM label
        |INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
        |INNER JOIN label_point ON label.label_id = label_point.label_id
        |WHERE label.deleted = false
        |    AND label_point.lat IS NOT NULL
        |    AND ST_Intersects(label_point.geom, ST_MakeEnvelope(?, ?, ?, ?, 4326))""".stripMargin
    )
    selectLabelLocationQuery((minLng, minLat, maxLng, maxLat)).list
  }

  /**
   * Returns a list of labels submitted by the given user.
   */
  def getLabelLocations(userId: UUID): List[LabelLocation] = db.withSession { implicit session =>
    val _labels = for {
      ((_auditTasks, _labels), _labelTypes) <- auditTasks leftJoin labelsWithoutDeletedOrOnboarding on(_.auditTaskId === _.auditTaskId) leftJoin labelTypes on (_._2.labelTypeId === _.labelTypeId)
      if _auditTasks.userId === userId.toString
    } yield (_labels.labelId, _labels.auditTaskId, _labels.gsvPanoramaId, _labelTypes.labelType, _labels.panoramaLat, _labels.panoramaLng)

    val _points = for {
      (l, p) <- _labels.innerJoin(labelPoints).on(_._1 === _.labelId)
    } yield (l._1, l._2, l._3, l._4, p.lat.getOrElse(0.toFloat), p.lng.getOrElse(0.toFloat))

    val labelLocationList: List[LabelLocation] = _points.list.map(label => LabelLocation(label._1, label._2, label._3, label._4, label._5, label._6))
    labelLocationList
  }

  def getLabelLocations(userId: UUID, regionId: Int): List[LabelLocation] = db.withSession { implicit session =>
    val selectQuery = Q.query[(String, Int), LabelLocation](
      """SELECT label.label_id,
        |       label.audit_task_id,
        |       label.gsv_panorama_id,
        |       label_type.label_type,
        |       label_point.lat,
        |       label_point.lng,
        |       region.region_id
        |FROM label
        |INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
        |INNER JOIN label_point ON label.label_id = label_point.label_id
        |INNER JOIN audit_task ON audit_task.audit_task_id = label.audit_task_id
        |INNER JOIN street_edge_region ON street_edge_region.street_edge_id = audit_task.street_edge_id
        |INNER JOIN region ON street_edge_region.region_id = region.region_id
        |WHERE label.deleted = FALSE
        |    AND label_point.lat IS NOT NULL
        |    AND region.deleted = FALSE
        |    AND audit_task.user_id = ?
        |    AND region.region_id = ?""".stripMargin
    )
    selectQuery((userId.toString, regionId)).list
  }

  /**
    * Returns a count of the number of labels placed on each day there were labels placed.
    */
  def selectLabelCountsPerDay: List[LabelCountPerDay] = db.withSession { implicit session =>
    val selectLabelCountQuery =  Q.queryNA[(String, Int)](
      """SELECT calendar_date, COUNT(label_id)
        |FROM
        |(
        |    SELECT label_id, task_start::date AS calendar_date
        |    FROM audit_task
        |    INNER JOIN label ON audit_task.audit_task_id = label.audit_task_id
        |    WHERE deleted = FALSE
        |) AS calendar
        |GROUP BY calendar_date
        |ORDER BY calendar_date""".stripMargin
    )
    selectLabelCountQuery.list.map(x => LabelCountPerDay.tupled(x))
  }

  /**
    * Select label counts per user.
    *
    * @return list of tuples of (user_id, role, label_count)
    */
  def getLabelCountsPerUser: List[(String, String, Int)] = db.withSession { implicit session =>

    val audits = for {
      _user <- users if _user.username =!= "anonymous"
      _userRole <- userRoles if _user.userId === _userRole.userId
      _role <- roleTable if _userRole.roleId === _role.roleId
      _audit <- auditTasks if _user.userId === _audit.userId
      _label <- labelsWithoutDeleted if _audit.auditTaskId === _label.auditTaskId
    } yield (_user.userId, _role.role, _label.labelId)

    // Counts the number of labels for each user by grouping by user_id and role.
    audits.groupBy(l => (l._1, l._2)).map { case ((uId, role), group) => (uId, role, group.length) }.list
  }


  /**
    * Select street_edge_id of street closest to lat/lng position.
    *
    * @return street_edge_id
    */
  def getStreetEdgeIdClosestToLatLng(lat: Float, lng: Float): Option[Int] = db.withSession { implicit session =>
    val selectStreetEdgeIdQuery = Q.query[(Float, Float), Int](
      """SELECT street_edge_id
         |FROM street_edge
         |WHERE deleted = FALSE
         |ORDER BY ST_Distance(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326)) ASC
         |LIMIT 1""".stripMargin
    )
    //NOTE: these parameters are being passed in correctly. ST_MakePoint accepts lng first, then lat.
    selectStreetEdgeIdQuery((lng, lat)).firstOption
  }

  /**
    * Gets the labels placed in the most recent mission.
    */
  def getLabelsFromCurrentAuditMission(regionId: Int, userId: UUID): List[Label] = db.withSession { implicit session =>
    val recentMissionId: Option[Int] = MissionTable.missions
        .filter(m => m.userId === userId.toString && m.regionId === regionId)
        .sortBy(_.missionStart.desc)
        .map(_.missionId).firstOption

    recentMissionId match {
      case Some(missionId) => labelsWithoutDeleted.filter(_.missionId === missionId).list
      case None => List()
    }
  }

  /**
   * Gets the labels placed by a user in a region.
   *
   * @param regionId Region ID to get labels from
   * @param userId User ID of user to find labels for
   * @return list of labels placed by user in region
   */
  def getLabelsFromUserInRegion(regionId: Int, userId: UUID): List[ResumeLabelMetadata] = db.withSession { implicit session =>
    val labelsInRegionQuery = Q.queryNA[ResumeLabelMetadata](
      s"""SELECT -- Entire label table.
        |       label.label_id, label.audit_task_id, label.mission_id, label.gsv_panorama_id, label.label_type_id,
        |       label.photographer_heading, label.photographer_pitch, label.panorama_lat, label.panorama_lng,
        |       label.deleted, label.temporary_label_id, label.time_created, label.tutorial, label.street_edge_id,
        |       label.agree_count, label.disagree_count, label.notsure_count, label.correct, label.severity,
        |       label.temporary, label.description,
        |       label_type.label_type,
        |       -- Entire label_point table.
        |       label_point_id, label_point.label_id, sv_image_x, sv_image_y, canvas_x, canvas_y, heading, pitch, zoom,
        |       canvas_height, canvas_width, alpha_x, alpha_y, lat, lng, geom, computation_method,
        |       -- All the extra stuff.
        |       gsv_data.image_width, gsv_data.image_height,
        |       the_tags.tag_list
        |FROM mission
        |INNER JOIN label ON mission.mission_id = label.mission_id
        |INNER JOIN label_point ON label.label_id = label_point.label_id
        |INNER JOIN label_type ON label.label_type_id = label_type.label_type_id
        |INNER JOIN gsv_data ON label.gsv_panorama_id = gsv_data.gsv_panorama_id
        |LEFT JOIN (
        |    -- Puts set of tag_ids associated with the label in a comma-separated list in a string.
        |    SELECT label_id, array_to_string(array_agg(tag_id), ',') AS tag_list
        |    FROM label_tag
        |    GROUP BY label_id
        |) the_tags
        |   ON label.label_id = the_tags.label_id
        |WHERE label.deleted = FALSE
        |   AND mission.region_id = $regionId
        |   AND mission.user_id = '${userId.toString}'
        |   AND label_point.lat IS NOT NULL AND label_point.lng IS NOT NULL;""".stripMargin
    )
    labelsInRegionQuery.list
  }
  
  /**
    * Get next temp label id to be used. That would be the max used + 1, or just 1 if no labels in this task.
    */
  def nextTempLabelId(userId: UUID): Int = db.withSession { implicit session =>
      val userLabels = for {
        m <- missions if m.userId === userId.toString
        l <- labels if l.missionId === m.missionId
      } yield l.temporaryLabelId
      userLabels.max.run.map(x => x + 1).getOrElse(1)
  }

  def deprioritizedLabels(): Set[Int] = db.withSession { implicit session =>
    // Get set of deprioritized labels (to not show) by filtering out those that have been validated as "disagree" 3 or
    // more times and have twice as many disagrees as agrees.
    Q.queryNA[(Int)](
      """SELECT label_id
        |FROM label
        |WHERE disagree_count > 2 AND disagree_count >= 2 * agree_count""".stripMargin
    ).list.toSet
  }

  /**
   * Get metadata used for 2022 CV project for all labels.
   */
  def getLabelCVMetadata: List[LabelCVMetadata] = db.withSession { implicit session =>
    (for {
      _l <- labels
      _lp <- labelPoints if _l.labelId === _lp.labelId
      _at <- auditTasks if _l.auditTaskId === _at.auditTaskId
      _gsv <- gsvData if _l.gsvPanoramaId === _gsv.gsvPanoramaId
      // Filter out deleted and tutorial labels.
      if !_l.deleted
      if !_l.tutorial && !(_l.streetEdgeId === tutorialStreetId) && !(_at.streetEdgeId === tutorialStreetId)
    } yield (
      _l.labelId, _gsv.gsvPanoramaId, _l.labelTypeId, _l.agreeCount, _l.disagreeCount, _l.notsureCount,
      _gsv.imageWidth, _gsv.imageHeight, _lp.svImageX, _lp.svImageY, _lp.canvasWidth, _lp.canvasHeight, _lp.canvasX,
      _lp.canvasY, _lp.zoom, _lp.heading, _lp.pitch, _l.photographerHeading, _l.photographerPitch
    )).list.map(LabelCVMetadata.tupled)
  }
}
