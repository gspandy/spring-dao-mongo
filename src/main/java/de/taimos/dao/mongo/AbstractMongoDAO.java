package de.taimos.dao.mongo;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.DB;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand.OutputType;
import com.mongodb.MapReduceOutput;
import com.mongodb.MongoClient;

import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.jongo.Find;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.marshall.jackson.JacksonMapper;
import org.jongo.marshall.jackson.JacksonMapper.Builder;
import org.springframework.beans.factory.annotation.Autowired;

import de.taimos.dao.AEntity;
import de.taimos.dao.ICrudDAO;
import de.taimos.dao.JodaMapping;

public abstract class AbstractMongoDAO<T extends AEntity> implements ICrudDAO<T> {
	
	@Autowired
	private MongoClient mongo;
	
	@Autowired
	private ObjectMapper mapper;
	
	private Jongo jongo;
	protected MongoCollection collection;
	
	
	@PostConstruct
	public void init() {
		DB db = this.mongo.getDB(System.getProperty("db.name", "semi-balance"));
		this.jongo = this.createJongo(db);
		this.collection = this.jongo.getCollection(this.getCollectionName());
		this.addIndexes();
	}
	
	protected void addIndexes() {
		// Override to add indexes
	}
	
	protected abstract String getCollectionName();
	
	protected abstract Class<T> getEntityClass();
	
	protected <T> Iterable<T> mapReduce(String map, String reduce, final IObjectConverter<T> conv) {
		return this.mapReduce(map, reduce, null, conv);
	}
	
	protected <T> Iterable<T> mapReduce(String map, String reduce, DBObject query, final IObjectConverter<T> conv) {
		MapReduceOutput mr = this.collection.getDBCollection().mapReduce(map, reduce, null, OutputType.INLINE, query);
		return new ConverterIterable<T>(mr.results().iterator(), conv);
	}
	
	@Override
	public List<T> findList() {
		Iterable<T> as = this.collection.find().sort("{_id:1}").as(this.getEntityClass());
		return this.convertIterable(as);
	}
	
	protected List<T> convertIterable(Iterable<T> as) {
		List<T> objects = new ArrayList<>();
		for (T mp : as) {
			objects.add(mp);
		}
		return objects;
	}
	
	protected List<T> findByQuery(String query, Object... params) {
		return this.findByQuery(query, null, params);
	}
	
	protected List<T> findSortedByQuery(String query, String sort, Object... params) {
		Find find = this.collection.find(query, params);
		if ((sort != null) && !sort.isEmpty()) {
			find.sort(sort);
		}
		return this.convertIterable(find.as(this.getEntityClass()));
	}
	
	@Override
	public T findById(String id) {
		return this.collection.findOne(new ObjectId(id)).as(this.getEntityClass());
	}
	
	@Override
	public T save(T object) {
		this.collection.save(object);
		return object;
	}
	
	@Override
	public void delete(T object) {
		this.delete(object.getId());
	}
	
	@Override
	public void delete(String id) {
		this.collection.remove(new ObjectId(id));
	}
	
	public Jongo createJongo(DB db) {
		Builder builder = new JacksonMapper.Builder();
		builder.enable(MapperFeature.AUTO_DETECT_GETTERS);
		builder.addSerializer(DateTime.class, new JodaMapping.MongoDateTimeSerializer());
		builder.addDeserializer(DateTime.class, new JodaMapping.MongoDateTimeDeserializer());
		builder.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
		builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		return new Jongo(db, builder.build());
	}
}