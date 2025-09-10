package ao.co.isptec.aplm.sca.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "ocorrencias")
data class OcorrenciaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteId: Long? = null,
    val descricao: String,
    val localizacaoSimbolica: String? = null,
    val latitude: Double,
    val longitude: Double,
    val dataHora: Date,
    val urgente: Boolean = false,
    val contadorPartilha: Int = 0,
    val fotoPath: String? = null,
    val videoPath: String? = null,
    val synced: Boolean = false,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)
