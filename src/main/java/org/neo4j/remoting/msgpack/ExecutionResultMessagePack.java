package org.neo4j.remoting.msgpack;

import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.cypher.javacompat.QueryStatistics;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.helpers.collection.MapUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.*;

import static org.neo4j.helpers.collection.MapUtil.map;

/**
 * @author mh
 * @since 20.01.13
 * ideas for saving storage, performance:
 * cache conversions (didn't help much)
 * only return a node/rel first time it comes along later use {node-id:id} or {id:-id}
 */
public class ExecutionResultMessagePack implements Iterator<byte[]> {
    private static final int FIRST = Integer.MIN_VALUE;
    private static final int LAST = Integer.MAX_VALUE;

    private final ExecutionResult result;
    private final Map<String,Object> externalInfo;
    private final boolean stats;

    private List<String> columns = null;
    private Iterator<Map<String, Object>> it;

    int row = FIRST;
    private final List<Object> data;
    private final long start;
    private Exception exception;
    private long bytes=0;
    private Boolean hasNext;

    public ExecutionResultMessagePack(ExecutionResult result, boolean stats, Map<String,Object> externalInfo) {
        this.result = result;
        this.externalInfo = externalInfo !=null ? externalInfo : Collections.<String,Object>emptyMap();
        this.stats = stats;
        try {
            if (result!=null) {
                columns = new ArrayList<String>(this.result.columns());
                it = this.result.iterator();
            }
        } catch (Exception e) {
            this.exception = e;
        }
        if (columns==null) {
            columns = Collections.emptyList();
            row = 0;
        }
        if (it==null) {
            it = emptyIterator();
            row = row == FIRST || returnStats() ? row : LAST;
        }
        data = new ArrayList<Object>(columns);
        start = System.currentTimeMillis();
    }

    public ExecutionResultMessagePack(ExecutionResult result) {
        this(result,false,Collections.<String,Object>emptyMap());
    }

    private Iterator<Map<String, Object>> emptyIterator() {
        return Collections.<Map<String, Object>>emptyList().iterator();
    }

    public boolean hasNext() {
        if (hasNext==null) {
            hasNext = row == FIRST || it.hasNext() || row != LAST || exception!=null;
        }
        return hasNext;
    }

    public byte[] next() {
        if (row == FIRST) {
            row = 0;
            return pack(columns);
        }
        if (it.hasNext()) {
            try {
                final Map<String, Object> current = it.next();
                for (int i = 0; i < columns.size(); i++) {
                    final Object value = current.get(columns.get(i));
                    data.set(i, convert(value));
                }
                if (it.hasNext() || returnStats()) {
                    row++;
                } else {
                    row=LAST;
                }
                return pack(data);
            } catch(Exception e) {
                exception = e;
                it = emptyIterator();
                return pack(info());
            }
        } else {
            return pack(info());
        }
    }

    private byte[] pack(Object data) {
        hasNext=null;
        final byte[] result = org.neo4j.remoting.msgpack.MsgPack.pack(data);
        bytes += result.length;
        return result;
    }

    private Map<String,Object> toMap(PropertyContainer pc) {
        final Iterator<String> propertyKeys = pc.getPropertyKeys().iterator();
        if (!propertyKeys.hasNext()) return null;
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        while (propertyKeys.hasNext()) {
            String prop = propertyKeys.next();
            final Object value = pc.getProperty(prop);
            if (value.getClass().isArray()) {
                result.put(prop, handleArray(value));
            } else {
                result.put(prop, value);
            }
        }
        return result;
    }
    private Object convert(Object value) {
        if (value == null) return null;
        if (value instanceof Node) {
            final Node node = (Node) value;
            return returnWithProps(map("id", node.getId()), node);
        }
        if (value instanceof Relationship) {
            Relationship relationship = (Relationship) value;
            return returnWithProps(map("id", relationship.getId(), "start", relationship.getStartNode().getId(), "end", relationship.getEndNode().getId(), "type", relationship.getType().name()), relationship);
        }
        if (value instanceof Path) {
            Path path = (Path) value;
            return map(
                    "length", path.length(),
                    "start", convert(path.startNode()),
                    "end", convert(path.endNode()),
                    "nodes", convert(path.nodes()),
                    "relationships", convert(path.relationships()));
        }
        if (value instanceof Iterable) {
            return convert(((Iterable) value).iterator());
        }
        if (value instanceof Iterator) {
            final ArrayList<Object> result = new ArrayList<Object>();
            Iterator iterator = (Iterator) value;
            while (iterator.hasNext()) {
                result.add(convert(iterator.next()));
            }
            return result;
        }
        if (value.getClass().isArray()) {
            return handleArray(value);
        }
        return value;
    }

    private List<?> handleArray(Object value) {
        if (value instanceof String[]) {
            return Arrays.asList((String[])value);
        }
        final int length = Array.getLength(value);
        final ArrayList<Object> result = new ArrayList<Object>(length);
        if (value instanceof int[]) {
            for (int v : (int[])value) result.add(v);
            return result;
        }
        if (value instanceof long[]) {
            for (long v : (long[])value) result.add(v);
            return result;
        }
        if (value instanceof byte[]) {
            for (byte v : (byte[])value) result.add(v);
            return result;
        }
        if (value instanceof double[]) {
            for (double v : (double[])value) result.add(v);
            return result;
        }
        if (value instanceof boolean[]) {
            for (boolean v : (boolean[])value) result.add(v);
            return result;
        }
        if (value instanceof short[]) {
            for (short v : (short[])value) result.add(v);
            return result;
        }
        if (value instanceof char[]) {
            for (char v : (char[])value) result.add(v);
            return result;
        }
        if (value instanceof float[]) {
            for (float v : (float[])value) result.add(v);
            return result;
        }
        for (int i=0;i < length;i++) {
            result.add(convert(Array.get(value,i)));
        }
        return result;
    }

    private Object returnWithProps(Map<String, Object> result, PropertyContainer pc) {
        final Map<String, Object> props = toMap(pc);
        if (props!=null) {
            result.put("data",props);
        }
        return result;
    }

    private Map<String, Object> info() {
        row = LAST;
        if (!returnStats()) {
            return Collections.emptyMap();
        }
        return createResultInfo();
    }

    public Map<String, Object> createResultInfo() {
        final Map<String, Object> info = MapUtil.map(
                "time", System.currentTimeMillis() - start,
                "rows", row,
                "bytes", bytes);
        info.putAll(externalInfo);
        if (this.result != null) {
            final QueryStatistics queryStats = this.result.getQueryStatistics();
            if (queryStats != null && queryStats.containsUpdates()) {
                info.put("updates", true);
                putIfValue(info, "nodes_deleted", queryStats.getDeletedNodes());
                putIfValue(info, "nodes_created", queryStats.getNodesCreated());
                putIfValue(info, "rels_created", queryStats.getRelationshipsCreated());
                putIfValue(info, "rels_deleted", queryStats.getDeletedRelationships());
                putIfValue(info, "props_set", queryStats.getPropertiesSet());
            }
        }
        if (exception!=null) {
            // TODO log
            exception.printStackTrace();
            addException(info,exception);
            exception = null;
        }
        return info;
    }

    private boolean returnStats() {
        return stats || exception != null;
    }

    public static void addException(Map<String, Object> result,Exception exception) {
        result.put("error", exception.getMessage());

        final StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        result.put("exception", writer.toString());
    }

    private void putIfValue(Map<String, Object> result, String name, int value) {
        if (value >0) {
            result.put(name, value);
        }
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
}
