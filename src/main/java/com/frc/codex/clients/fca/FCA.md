# FCA API
The FCA search functionality does not have a documented API.   
Instead, we can recreate the requests generated on [their search page](https://data.fca.org.uk/#/nsm/nationalstoragemechanism) to retrieve the data we are looking for.

- 10,000 appears to be the page size limit.

Example search request:
```
POST https://api.data.fca.org.uk/search?index=nsm-search
{
  "from": 0,
  "size": 50,
  "sortorder": "desc",
  "criteriaObj": {
    "criteria": [
      {
        "name": "tag_esef",
        "value": [
          "Tagged"
        ]
      }
    ],
    "dateCriteria": [
      {
        "name": "publication_date",
        "value": {
          "from": null,
          "to": "2024-09-06T16:49:00Z"
        }
      },
      {
        "name": "submitted_date",
        "value": {
          "from": "2024-01-01T00:00:00Z",
          "to": "2024-09-06T16:49:00Z"
        }
      }
    ]
  },
  "sort": "submitted_date"
}
```

Example response:
```
{
  "took": 77,
  "timed_out": false,
  "_shards": {
    "total": 4,
    "successful": 4,
    "skipped": 0,
    "failed": 0
  },
  "hits": {
    "total": {
      "value": 540,
      "relation": "eq"
    },
    "max_score": null,
    "hits": [
      {
        "_index": "nsm-search",
        "_type": "_doc",
        "_id": "NI-000103799-0",
        "_score": null,
        "_source": {
          "submitted_date": "2024-09-05T16:53:58.383Z",
          "tag_esef": "Tagged",
          "document_date": "2024-03-31",
          "classifications_code": "1.1",
          "source": "Portal",
          "ContentVersionId": "068Tw00000DKrDqIAL",
          "type": "Annual Financial Report",
          "lei": "213800PUHEBLCWDW9T74",
          "classifications": "Annual financial and audit reports",
          "ProcessType": "ESEF",
          "download_link": "NSM/Portal/NI-000103799/NI-000103799_213800PUHEBLCWDW9T74-2024-03-31.zip",
          "publication_date": "2024-07-25T06:00:00.000Z",
          "seq_id": "NI-000103799-0",
          "company": "Amigo Holdings PLC",
          "html_link": "NSM/Portal/NI-000103799/reports/213800PUHEBLCWDW9T74-2024-03-31-T01.xhtml",
          "RegionOfIncorporation": "UK",
          "headline": "Annual Financial Reports in Structured Electronic Format",
          "type_code": "ACS",
          "IsTestSubmission": false
        },
        "sort": [
          1725555238383
        ]
      },
      ...
    ]
  }
}
```
