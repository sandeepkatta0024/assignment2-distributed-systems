# assignment2-distributed-systems

gradle run -PmainClass=assignment2.AggregationServer --args="4567"

gradle run -PmainClass=assignment2.ContentServer --args="localhost:4567 src/main/java/resources/weather_data.txt"


gradle run -PmainClass=assignment2.GETClient --args="localhost:4567"