import Images.Companion.logger
import ij.IJ
import ij.process.ColorProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.imagej.processing.RoiLabeling
import qupath.imagej.tools.IJTools
import qupath.lib.gui.commands.ProjectCommands
import qupath.lib.images.ImageData
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.objects.PathObject
import qupath.lib.objects.PathObjects
import qupath.lib.projects.Project
import qupath.lib.regions.ImagePlane
import java.awt.image.BufferedImage
import java.io.File

class Images {
  companion object {
    val logger: Logger = LoggerFactory.getLogger(Images::class.java)
  }
}

fun Project<BufferedImage>.addImages(inputImages: List<InputImage>) {
  val logger = Images.logger

  inputImages
    .mapNotNull { it.localPath }
    .map { File(it) }
    .forEach { file ->
      val imagePath = file.getCanonicalPath()
      logger.debug("Considering: $imagePath")

      val support = ImageServerProvider.getPreferredUriImageSupport(BufferedImage::class.java, imagePath)
      val builder = support.builders[0]

      if (builder == null) {
        logger.info("No builder found for $imagePath; skipping")
        return@forEach
      }

      logger.info("Adding ${file.name} using builder: ${builder.javaClass.simpleName}")

      val entry = this.addImage(builder)

      val imageData = entry.readImageData()
      imageData.imageType = ImageData.ImageType.FLUORESCENCE
      entry.saveImageData(imageData)

      val img = ProjectCommands.getThumbnailRGB(imageData.server)
      entry.thumbnail = img

      entry.imageName = file.name
    }

  this.syncChanges()
}

fun extractPathObjects(
  maskPath: String,
  downsample: Double = 1.0,
  imagePlane: ImagePlane = ImagePlane.getDefaultPlane(),
): List<PathObject> {
  val imp = IJ.openImage(maskPath)
  logger.info(imp.toString())
  val n = imp.statistics.max.toInt()
  logger.info("   Max Cell Label: $n")
  if (n == 0) {
    logger.info(" >>> No objects found! <<<")
    return emptyList()
  }

  val ip = imp.processor
  if (ip is ColorProcessor) {
    throw IllegalArgumentException("RGB images are not supported!")
  }

  val roisIJ = RoiLabeling.labelsToConnectedROIs(ip, n)
  val rois = roisIJ.mapNotNull {
    if (it == null) {
      null
    } else {
      IJTools.convertToROI(it, 0.0, 0.0, downsample, imagePlane)
    }
  }

  return rois.map { PathObjects.createDetectionObject(it) }
}