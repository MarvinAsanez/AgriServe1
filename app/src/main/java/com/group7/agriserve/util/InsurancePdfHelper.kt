package com.group7.agriserve.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates official pre-filled PCIC Crop Insurance PDF forms offline:
 * - HVCI: HVCI CAS Form No. 01 (REV. 2022/MAR) - Notice of Loss (High-Value Crop Insurance)
 * - CLAIM_INDEMNITY: RC-CAS-01 (Rev. 2017/FEB) - Claim for Indemnity (Rice & Corn)
 */
object InsurancePdfHelper {

    data class InsuranceFormData(
        val formType: String, // "HVCI" or "CLAIM_INDEMNITY"
        val farmerName: String,
        val contact: String,
        val farmLocation: String,
        val cropCategory: String,
        val causeOfLoss: String,
        val dateOfLoss: String,
        val regionalOffice: String = "",
        val policyNo: String = "",
        val daNotes: String = "",
        val varietyPlanted: String = "",
        val areaInsured: String = "",
        val areaDamaged: String = "",
        val plantingDate: String = "",
        val cropStage: String = "",
        val programType: String = "RSBSA",
        val expectedHarvestDate: String = "",
        val extentOfDamage: String = "",
        val policyType: String = "High-Value Crop",
        val adjacentNorth: String = "",
        val adjacentSouth: String = "",
        val adjacentEast: String = "",
        val adjacentWest: String = ""
    )

    fun detectFormType(rawFormType: String?, category: String, description: String): String {
        if (!rawFormType.isNullOrBlank()) return rawFormType
        val hay = "$category $description".lowercase(Locale.ROOT)
        return if (hay.contains("rice") || hay.contains("palay") || hay.contains("corn") || hay.contains("mais")) {
            "CLAIM_INDEMNITY"
        } else {
            "HVCI"
        }
    }

    fun generateAndOpenForm(context: Context, data: InsuranceFormData) {
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 points
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 13f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            val smallPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            val linePaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = 0.8f
            }

            val todayStr = SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date())

            if (data.formType == "CLAIM_INDEMNITY") {
                drawClaimForIndemnity(canvas, data, todayStr, titlePaint, boldPaint, bodyPaint, smallPaint, linePaint)
            } else {
                drawHvciNoticeOfLoss(canvas, data, todayStr, titlePaint, boldPaint, bodyPaint, smallPaint, linePaint)
            }

            pdf.finishPage(page)

            val fileName = if (data.formType == "CLAIM_INDEMNITY") {
                "RC_CAS_01_Claim_for_Indemnity_${System.currentTimeMillis()}.pdf"
            } else {
                "HVCI_CAS_Form_01_Notice_of_Loss_${System.currentTimeMillis()}.pdf"
            }

            val uri = savePdfToStorage(context, pdf, fileName)
            if (uri != null) {
                Toast.makeText(context, "Saved $fileName to Downloads!", Toast.LENGTH_LONG).show()
                openPdfUri(context, uri)
            } else {
                Toast.makeText(context, "Unable to save PDF form.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            pdf.close()
        }
    }

    private fun drawHvciNoticeOfLoss(
        canvas: android.graphics.Canvas,
        data: InsuranceFormData,
        todayStr: String,
        titlePaint: Paint,
        boldPaint: Paint,
        bodyPaint: Paint,
        smallPaint: Paint,
        linePaint: Paint
    ) {
        canvas.drawText("HVCI CAS Form No. 01", 445f, 45f, smallPaint)
        canvas.drawText("REV. 2022/MAR", 445f, 57f, smallPaint)

        titlePaint.textAlign = Paint.Align.CENTER
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("PHILIPPINE CROP INSURANCE CORPORATION", 297f, 90f, titlePaint)
        canvas.drawText("Regional Office No. ${data.regionalOffice.ifBlank { "____" }}", 297f, 106f, boldPaint)
        canvas.drawText("NOTICE OF LOSS", 297f, 130f, titlePaint)
        canvas.drawText("(High-Value Crop Insurance)", 297f, 146f, boldPaint)
        titlePaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.LEFT

        canvas.drawText(todayStr, 430f, 185f, bodyPaint)
        canvas.drawLine(400f, 189f, 540f, 189f, linePaint)
        canvas.drawText("Date", 455f, 202f, smallPaint)

        canvas.drawText("TO : THE REGIONAL MANAGER", 60f, 232f, bodyPaint)
        canvas.drawLine(90f, 250f, 310f, 250f, linePaint)
        canvas.drawLine(90f, 266f, 310f, 266f, linePaint)

        canvas.drawText("Dear Sir/Madam:", 60f, 300f, bodyPaint)
        canvas.drawText("This is to file a NOTICE OF LOSS with respect to the ${data.policyType.ifBlank { "High-Value Crop" }} Insurance Policy No.", 85f, 326f, bodyPaint)
        val cropVarText = data.varietyPlanted.ifBlank { data.cropCategory }.ifBlank { "________________" }
        canvas.drawText("${data.policyNo.ifBlank { "__________________" }} for the coverage of $cropVarText crops issued by PCIC,", 60f, 344f, bodyPaint)
        canvas.drawText("with the following particulars:", 60f, 362f, bodyPaint)

        val rows = listOf(
            "1. Farm Location" to data.farmLocation,
            "2. Nature/cause of loss" to data.causeOfLoss.take(48),
            "3. Date/time of loss" to data.dateOfLoss,
            "4. Extent of loss/damage" to data.extentOfDamage,
            "5. Expected date of harvest (if applicable)" to data.expectedHarvestDate,
            "6. Contact number" to data.contact
        )
        var y = 405f
        rows.forEach { (label, value) ->
            canvas.drawText(label, 85f, y, bodyPaint)
            canvas.drawText(value, 295f, y - 2f, bodyPaint)
            canvas.drawLine(290f, y + 3f, 535f, y + 3f, linePaint)
            y += 28f
        }

        canvas.drawText("Very truly yours,", 60f, y + 34f, bodyPaint)
        canvas.drawText(data.farmerName, 165f, y + 88f, boldPaint)
        canvas.drawLine(85f, y + 92f, 285f, y + 92f, linePaint)
        canvas.drawText("Signature Over Printed Name", 115f, y + 106f, smallPaint)
        canvas.drawText("of the Insured/Claimant", 125f, y + 118f, smallPaint)

        if (data.daNotes.isNotBlank()) {
            canvas.drawText("DA/MAO Endorsement Note: ${data.daNotes.take(80)}", 60f, 790f, smallPaint)
        }
    }

    private fun drawClaimForIndemnity(
        canvas: android.graphics.Canvas,
        data: InsuranceFormData,
        todayStr: String,
        titlePaint: Paint,
        boldPaint: Paint,
        bodyPaint: Paint,
        smallPaint: Paint,
        linePaint: Paint
    ) {
        canvas.drawText("RC-CAS-01", 470f, 40f, smallPaint)
        canvas.drawText("Rev. 2017/FEB", 470f, 52f, smallPaint)

        titlePaint.textAlign = Paint.Align.CENTER
        boldPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("CLAIM FOR INDEMNITY", 297f, 78f, titlePaint)
        canvas.drawText("(PAGHAHABOL BAYAD)", 297f, 94f, boldPaint)
        titlePaint.textAlign = Paint.Align.LEFT
        boldPaint.textAlign = Paint.Align.LEFT

        canvas.drawText(todayStr, 435f, 118f, bodyPaint)
        canvas.drawLine(400f, 122f, 540f, 122f, linePaint)
        canvas.drawText("DATE (PETSA)", 440f, 134f, smallPaint)

        canvas.drawText("TO : The Chief CAD, PCIC-RO ${data.regionalOffice}", 55f, 152f, boldPaint)
        canvas.drawText("Please send your Team of Adjusters to assess damage of my insured crop.", 55f, 174f, smallPaint)
        canvas.drawText("Hereunder are the basic information needed by your office:", 55f, 188f, smallPaint)

        var y = 214f
        canvas.drawText("I. BASIC INFORMATION (MGA PANGUNAHING IMPORMASYON):", 55f, y, boldPaint)
        y += 20f

        val isCorn = (data.varietyPlanted + " " + data.cropCategory).lowercase(Locale.ROOT).let { it.contains("corn") || it.contains("mais") }
        val prog = data.programType.ifBlank { "RSBSA" }
        val basicRows = listOf(
            "1. Name of Farmer-Assured:" to data.farmerName,
            "2. Address:" to data.farmLocation,
            "3. Cell Phone Number:" to data.contact,
            "4. Location of Farm:" to data.farmLocation,
            "5. Insured Crops:" to if (isCorn) "[ ] Palay   [X] Corn" else "[X] Palay   [ ] Corn",
            "6. Area Insured (ha):" to data.areaInsured,
            "7. Variety Planted:" to data.varietyPlanted.ifBlank { data.cropCategory },
            "8. Actual Date of Planting:" to data.plantingDate,
            "9. CIC Number:" to data.policyNo,
            "10. Underwriter/Cooperative:" to "",
            "11. Program:" to prog
        )
        basicRows.forEach { (label, valText) ->
            canvas.drawText(label, 65f, y, bodyPaint)
            canvas.drawText(valText.take(45), 240f, y - 1f, bodyPaint)
            canvas.drawLine(235f, y + 3f, 540f, y + 3f, linePaint)
            y += 20f
        }

        y += 10f
        canvas.drawText("II. DAMAGE INDICATORS (MGA IMPORMASYON TUNGKOL SA PINSALA):", 55f, y, boldPaint)
        y += 20f
        val damageRows = listOf(
            "1. Cause of Loss:" to data.causeOfLoss.take(45),
            "2. Date of Loss Occurrence:" to data.dateOfLoss,
            "3. Age/Stage of Cultivation:" to data.cropStage,
            "4. Area Damaged (ha):" to data.areaDamaged,
            "5. Extent/Degree of Damage (%):" to data.extentOfDamage,
            "6. Expected Date of Harvest:" to data.expectedHarvestDate
        )
        damageRows.forEach { (label, valText) ->
            canvas.drawText(label, 65f, y, bodyPaint)
            canvas.drawText(valText, 240f, y - 1f, bodyPaint)
            canvas.drawLine(235f, y + 3f, 540f, y + 3f, linePaint)
            y += 20f
        }

        y += 10f
        canvas.drawText("III. LOCATION SKETCH PLAN OF DAMAGED INSURED CROPS (LSP)", 55f, y, boldPaint)
        y += 18f
        listOf(
            "1. North (Hilaga)" to data.adjacentNorth,
            "2. South (Timog)" to data.adjacentSouth,
            "3. East (Silangan)" to data.adjacentEast,
            "4. West (Kanluran)" to data.adjacentWest
        ).forEach { (dir, owner) ->
            canvas.drawText(dir, 65f, y, bodyPaint)
            canvas.drawText(owner.take(42), 190f, y - 1f, bodyPaint)
            canvas.drawLine(185f, y + 3f, 540f, y + 3f, linePaint)
            y += 18f
        }

        canvas.drawText(data.farmerName, 360f, 765f, boldPaint)
        canvas.drawLine(320f, 769f, 540f, 769f, linePaint)
        canvas.drawText("Signature over Printed Name of Assured Farmer-Claimant", 322f, 782f, smallPaint)

        if (data.daNotes.isNotBlank()) {
            canvas.drawText("DA/MAO Endorsement Note: ${data.daNotes.take(80)}", 55f, 812f, smallPaint)
        }
    }

    private fun savePdfToStorage(context: Context, pdf: PdfDocument, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AgriServe")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) }
                    return uri
                }
            }
            val dir = File(context.cacheDir, "insurance_forms")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> pdf.writeTo(out) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun openPdfUri(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open PCIC Insurance Form"))
        } catch (_: Exception) {
            Toast.makeText(context, "PDF saved to Downloads/AgriServe. Open with any PDF viewer.", Toast.LENGTH_LONG).show()
        }
    }
}
