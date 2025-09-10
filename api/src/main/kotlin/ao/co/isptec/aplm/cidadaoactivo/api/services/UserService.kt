package ao.co.isptec.aplm.cidadaoactivo.api.services

import ao.co.isptec.aplm.cidadaoactivo.api.models.User
import ao.co.isptec.aplm.cidadaoactivo.api.repositories.UserRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.Date

@Service
class UserService(private val db: UserRepository) {
    
    fun getAllUsers(): List<User> = db.findAll()
    
    fun getUserById(id: Long): User? = db.findById(id).orElse(null)
    
    fun createUser(user: User): User? {
        if (db.findByUsername(user.username) != null) {
            return null
        }
        val newUser = user.copy(id = null, sessionToken = null)
        return db.save(newUser)
    }
    
    fun saveUser(id: Long, user: User): User? {
        if (!db.existsById(id)) return null
        val user = user.copy(id = id)
        return db.save(user)        
    }
    
    fun deleteUser(id: Long) {
        db.deleteById(id)
    }
    
    fun login(username: String, password: String): User? {
        var user = db.findByUsername(username) ?: return null
        if (user.password != password) return null
        val token = generateHash(user)
        user = user.copy(sessionToken = token)
        saveUser(user.id!!, user)
        return user
    }
    
    fun logout(username: String, token: String): Boolean {
        val user = db.findByUsername(username) ?: return false
        if (user.sessionToken != token) return false
        saveUser(user.id!!, user.copy(sessionToken = null))
        return true
    }
    
    @OptIn(ExperimentalStdlibApi::class)
    private fun generateHash(user: User): String {
        val bytes = "${user}${Date.from(Instant.now())}".toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.toHexString() 
    }
}